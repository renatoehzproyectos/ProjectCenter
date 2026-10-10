package com.projectcenter.app.data.github;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Builds a git packfile (blobs + trees + one commit) for a directory of files, entirely
 * streaming from disk. Used to push with the git smart-HTTP protocol in a single request,
 * which avoids per-file API calls, GitHub's API rate limits, and holding files in memory.
 *
 * Pure Java (no Android dependencies) so it can be unit-tested on a desktop JVM.
 */
public final class GitPack {

    public interface Listener {
        void onStage(String label, long done, long total);
    }

    public interface Cancel {
        boolean isCancelled();
    }

    private static final int TYPE_COMMIT = 1;
    private static final int TYPE_TREE = 2;
    private static final int TYPE_BLOB = 3;
    private static final int BUF = 128 * 1024;

    private static final Set<String> STORE_ONLY_EXT = new HashSet<>(Arrays.asList(
            "png", "jpg", "jpeg", "gif", "webp", "zip", "gz", "tgz", "bz2", "xz", "7z", "rar",
            "jar", "apk", "aar", "mp3", "mp4", "m4a", "mov", "mkv", "avi", "webm", "ogg",
            "woff", "woff2", "pdf", "br", "zst", "heic", "aab"));

    private GitPack() {
    }

    /** A finished pack, stored as part files in a temp directory. */
    public static final class Result implements Closeable {
        public final String commitSha;
        public final String treeSha;
        public final int objectCount;
        public final int fileCount;
        private final File dir;
        private final List<File> parts;
        private final long packLength;

        Result(String commitSha, String treeSha, int objectCount, int fileCount,
               File dir, List<File> parts, long packLength) {
            this.commitSha = commitSha;
            this.treeSha = treeSha;
            this.objectCount = objectCount;
            this.fileCount = fileCount;
            this.dir = dir;
            this.parts = parts;
            this.packLength = packLength;
        }

        /** Exact number of bytes {@link #writePack} will write. */
        public long packLength() {
            return packLength;
        }

        /** Streams "PACK" header, all objects and the SHA-1 trailer to {@code out}. */
        public void writePack(OutputStream out) throws IOException {
            MessageDigest md = sha1();
            DigestOutputStream dos = new DigestOutputStream(out, md);
            byte[] header = new byte[12];
            header[0] = 'P';
            header[1] = 'A';
            header[2] = 'C';
            header[3] = 'K';
            header[7] = 2; // version 2
            header[8] = (byte) (objectCount >>> 24);
            header[9] = (byte) (objectCount >>> 16);
            header[10] = (byte) (objectCount >>> 8);
            header[11] = (byte) objectCount;
            dos.write(header);
            byte[] buf = new byte[256 * 1024];
            for (File part : parts) {
                try (InputStream in = new FileInputStream(part)) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        dos.write(buf, 0, n);
                    }
                }
            }
            out.write(md.digest());
            out.flush();
        }

        @Override
        public void close() {
            deleteRecursively(dir);
        }
    }

    /**
     * @param root         project root directory
     * @param paths        file paths relative to root, using '/' separators
     * @param parentSha    parent commit sha (40 hex) or null for a root commit
     * @param authorLine   e.g. "Name <email>"
     * @param message      commit message
     * @param workDir      empty temp directory (created if missing)
     */
    public static Result build(File root, List<String> paths, String parentSha, String authorLine,
                               String message, File workDir, int threads,
                               Listener listener, Cancel cancel) throws Exception {
        workDir.mkdirs();
        final int n = paths.size();
        final byte[] shas = new byte[n * 20];
        final long[] sizes = new long[n];
        final boolean[] exec = new boolean[n];

        // ---- Phase 1: hash every file (parallel) -------------------------------------------
        final AtomicInteger hashed = new AtomicInteger();
        runWorkers(threads, cancel, (worker) -> {
            MessageDigest md = sha1();
            byte[] buf = new byte[BUF];
            int i;
            while ((i = nextIndex(worker.next, n)) >= 0) {
                checkCancel(cancel);
                File f = new File(root, paths.get(i));
                long len = f.length();
                sizes[i] = len;
                exec[i] = f.canExecute();
                md.reset();
                md.update(("blob " + len + "\0").getBytes(StandardCharsets.US_ASCII));
                long read = 0;
                try (InputStream in = new FileInputStream(f)) {
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        md.update(buf, 0, r);
                        read += r;
                    }
                }
                if (read != len) throw new IOException("File changed while reading: " + paths.get(i));
                System.arraycopy(md.digest(), 0, shas, i * 20, 20);
                int d = hashed.incrementAndGet();
                if (listener != null && (d % 512 == 0 || d == n)) {
                    listener.onStage("Hashing files…", d, n);
                }
            }
        });

        // ---- De-duplicate identical blobs (a pack must not repeat an object) ---------------
        Set<String> seen = new HashSet<>();
        int[] unique = new int[n];
        int uCount = 0;
        for (int i = 0; i < n; i++) {
            if (seen.add(hex(shas, i * 20))) unique[uCount++] = i;
        }
        final int uniqueCount = uCount;

        // ---- Phase 2: deflate unique blobs into per-worker part files (parallel) -----------
        final int workers = Math.max(1, Math.min(threads, Math.max(1, uniqueCount)));
        final File[] blobParts = new File[workers];
        for (int w = 0; w < workers; w++) blobParts[w] = new File(workDir, "blobs-" + w + ".part");
        final AtomicInteger workerIds = new AtomicInteger();
        final AtomicInteger done = new AtomicInteger();
        final int[] uniq = unique;
        runWorkers(workers, cancel, (worker) -> {
            int id = workerIds.getAndIncrement();
            byte[] buf = new byte[BUF];
            Deflater deflater = new Deflater(1, false);
            try (BufferedOutputStream out = new BufferedOutputStream(
                    new FileOutputStream(blobParts[id]), 256 * 1024)) {
                int k;
                while ((k = nextIndex(worker.next, uniqueCount)) >= 0) {
                    checkCancel(cancel);
                    int i = uniq[k];
                    String path = paths.get(i);
                    writeObjectHeader(out, TYPE_BLOB, sizes[i]);
                    deflater.reset();
                    deflater.setLevel(storeOnly(path) ? Deflater.NO_COMPRESSION : 1);
                    DeflaterOutputStream dos = new DeflaterOutputStream(out, deflater, 64 * 1024);
                    long read = 0;
                    try (InputStream in = new FileInputStream(new File(root, path))) {
                        int r;
                        while ((r = in.read(buf)) > 0) {
                            dos.write(buf, 0, r);
                            read += r;
                        }
                    }
                    if (read != sizes[i]) throw new IOException("File changed while reading: " + path);
                    dos.finish(); // finish the zlib stream but keep `out` open
                    int d = done.incrementAndGet();
                    if (listener != null && (d % 256 == 0 || d == uniqueCount)) {
                        listener.onStage("Compressing files…", d, uniqueCount);
                    }
                }
            } finally {
                deflater.end();
            }
        });

        // ---- Trees + commit (small; single thread) ----------------------------------------
        final File metaFile = new File(workDir, "meta.part");
        int treeCount;
        String treeHex;
        String commitHex;
        try (BufferedOutputStream meta = new BufferedOutputStream(new FileOutputStream(metaFile), 64 * 1024)) {
            Dir rootDir = new Dir();
            for (int i = 0; i < n; i++) {
                String p = paths.get(i);
                Dir d = rootDir;
                int start = 0;
                while (true) {
                    int slash = p.indexOf('/', start);
                    if (slash < 0) {
                        d.files.put(p.substring(start), i);
                        break;
                    }
                    String name = p.substring(start, slash);
                    Dir child = d.dirs.get(name);
                    if (child == null) {
                        child = new Dir();
                        d.dirs.put(name, child);
                    }
                    d = child;
                    start = slash + 1;
                }
            }
            TreeWriter tw = new TreeWriter(meta, shas, exec);
            byte[] rootSha = tw.write(rootDir);
            treeCount = tw.count;
            treeHex = hex(rootSha, 0);

            long now = System.currentTimeMillis() / 1000L;
            StringBuilder c = new StringBuilder();
            c.append("tree ").append(treeHex).append('\n');
            if (parentSha != null && !parentSha.isEmpty()) c.append("parent ").append(parentSha).append('\n');
            c.append("author ").append(authorLine).append(' ').append(now).append(" +0000\n");
            c.append("committer ").append(authorLine).append(' ').append(now).append(" +0000\n\n");
            c.append(message).append('\n');
            byte[] commit = c.toString().getBytes(StandardCharsets.UTF_8);
            byte[] commitSha = objectSha("commit", commit);
            commitHex = hex(commitSha, 0);
            writeWholeObject(meta, TYPE_COMMIT, commit);
        }

        // ---- Assemble result ----------------------------------------------------------------
        List<File> parts = new ArrayList<>();
        long total = 12 + 20;
        for (File f : blobParts) {
            if (f.length() > 0) {
                parts.add(f);
                total += f.length();
            }
        }
        parts.add(metaFile);
        total += metaFile.length();
        int objectCount = uniqueCount + treeCount + 1;
        return new Result(commitHex, treeHex, objectCount, n, workDir, parts, total);
    }

    // ================================================================================== trees

    private static final class Dir {
        final TreeMap<String, Dir> dirs = new TreeMap<>();
        final TreeMap<String, Integer> files = new TreeMap<>();
    }

    private static final class Ent {
        final byte[] name;
        final boolean dir;
        final String mode;
        final byte[] sha;

        Ent(byte[] name, boolean dir, String mode, byte[] sha) {
            this.name = name;
            this.dir = dir;
            this.mode = mode;
            this.sha = sha;
        }
    }

    private static final class TreeWriter {
        final OutputStream out;
        final byte[] fileShas;
        final boolean[] exec;
        final Set<String> seen = new HashSet<>();
        int count = 0;

        TreeWriter(OutputStream out, byte[] fileShas, boolean[] exec) {
            this.out = out;
            this.fileShas = fileShas;
            this.exec = exec;
        }

        byte[] write(Dir d) throws IOException {
            List<Ent> ents = new ArrayList<>(d.dirs.size() + d.files.size());
            for (java.util.Map.Entry<String, Dir> e : d.dirs.entrySet()) {
                ents.add(new Ent(e.getKey().getBytes(StandardCharsets.UTF_8), true, "40000", write(e.getValue())));
            }
            for (java.util.Map.Entry<String, Integer> e : d.files.entrySet()) {
                int i = e.getValue();
                ents.add(new Ent(e.getKey().getBytes(StandardCharsets.UTF_8), false,
                        exec[i] ? "100755" : "100644", Arrays.copyOfRange(fileShas, i * 20, i * 20 + 20)));
            }
            // git order: bytewise on name, with directories compared as if they ended in '/'
            ents.sort(new Comparator<Ent>() {
                @Override
                public int compare(Ent a, Ent b) {
                    int la = a.name.length + (a.dir ? 1 : 0);
                    int lb = b.name.length + (b.dir ? 1 : 0);
                    int m = Math.min(la, lb);
                    for (int k = 0; k < m; k++) {
                        int x = (k < a.name.length ? a.name[k] : '/') & 0xff;
                        int y = (k < b.name.length ? b.name[k] : '/') & 0xff;
                        if (x != y) return x - y;
                    }
                    return la - lb;
                }
            });
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            for (Ent e : ents) {
                bos.write(e.mode.getBytes(StandardCharsets.US_ASCII));
                bos.write(' ');
                bos.write(e.name);
                bos.write(0);
                bos.write(e.sha);
            }
            byte[] content = bos.toByteArray();
            byte[] sha = objectSha("tree", content);
            if (seen.add(hex(sha, 0))) {
                writeWholeObject(out, TYPE_TREE, content);
                count++;
            }
            return sha;
        }
    }

    // ================================================================================ helpers

    private interface Worker {
        void run(WorkerCtx ctx) throws Exception;
    }

    private static final class WorkerCtx {
        final AtomicInteger next;

        WorkerCtx(AtomicInteger next) {
            this.next = next;
        }
    }

    private static int nextIndex(AtomicInteger next, int limit) {
        int i = next.getAndIncrement();
        return i < limit ? i : -1;
    }

    private static void runWorkers(int threads, Cancel cancel, Worker w) throws Exception {
        final AtomicInteger next = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] ts = new Thread[Math.max(1, threads)];
        for (int t = 0; t < ts.length; t++) {
            ts[t] = new Thread(() -> {
                try {
                    w.run(new WorkerCtx(next));
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                    next.set(Integer.MAX_VALUE / 2); // stop the other workers
                }
            }, "gitpack-" + t);
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        Throwable f = failure.get();
        if (f instanceof Exception) throw (Exception) f;
        if (f instanceof Error) throw (Error) f;
    }

    private static void checkCancel(Cancel c) throws IOException {
        if (c != null && c.isCancelled()) throw new IOException("Cancelled");
    }

    private static boolean storeOnly(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return false;
        return STORE_ONLY_EXT.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static void writeObjectHeader(OutputStream o, int type, long size) throws IOException {
        long s = size;
        int b = (type << 4) | (int) (s & 0x0F);
        s >>>= 4;
        while (s != 0) {
            o.write(b | 0x80);
            b = (int) (s & 0x7F);
            s >>>= 7;
        }
        o.write(b);
    }

    private static void writeWholeObject(OutputStream o, int type, byte[] content) throws IOException {
        writeObjectHeader(o, type, content.length);
        Deflater d = new Deflater(1, false);
        try {
            DeflaterOutputStream dos = new DeflaterOutputStream(o, d, 8192);
            dos.write(content);
            dos.finish();
        } finally {
            d.end();
        }
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] objectSha(String type, byte[] content) {
        MessageDigest md = sha1();
        md.update((type + " " + content.length + "\0").getBytes(StandardCharsets.US_ASCII));
        md.update(content);
        return md.digest();
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static String hex(byte[] b, int off) {
        char[] c = new char[40];
        for (int i = 0; i < 20; i++) {
            int v = b[off + i] & 0xff;
            c[i * 2] = HEX[v >>> 4];
            c[i * 2 + 1] = HEX[v & 0x0f];
        }
        return new String(c);
    }

    private static void deleteRecursively(File f) {
        if (f == null) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursively(k);
        f.delete();
    }
}
