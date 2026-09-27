# ProjectCenter — Modification Notes

## Completed features

### 1. File Manager (`ui/filemanager/`, `core/storage/`)
- Opens at `/storage/emulated/0/Download`
- Sorted by lastModified DESC
- Lightweight directory watch (~2.5s) while screen is open
- Search, sort, multi-select, create folder
- Rename via long-press
- Safe overwrite dialog (existing size vs new size)
- Related-file detection (name normalize, size, ZIP root, directories)
- Select related / Organize into folder with confirmation
- Delete selected with explicit confirmation
- Bottom nav tab: **Files**

### 2. Automatic Push (`ui/projects/automatic/`, `PushConfigStore`)
- New action on ZIP action screen
- Persists project → GitHub owner/repo/branch
- One-tap PUSH when association exists (skips intermediate confirm when root is unambiguous)
- Configure & Push when missing
- **PUSH + DEPLOY** triggers Vercel create-or-deploy after successful push
- Association saved after every successful push

### 3. GitHub → Vercel
- Vercel screen: **DEPLOY GitHub Project**
- Creates Vercel project if none exists, then deploys
- Reuses `VercelRepository` / `VercelApi`

### Reconstructed missing core (export was incomplete)
- `core/security/SecureTokenStore`
- `core/storage/*` (FileAccessPermission, RecentZipScanner, FileManager, FileOperations, RelatedFileDetector, DownloadsWriter)
- `core/zip/*` (SafeZipExtractor, ZipAnalyzer, ZipRootDetector, ProjectFileLister, ErrorExtractor)
- `core/apk/ApkInstaller`
- `domain/models/Models.kt`
- AndroidManifest, resources, placeholder icon

## Preserved (not duplicated)
- GitHub auth, repo CRUD, backup-before-delete
- ZIP import/extract/root detection/uploader
- Workflow monitor, artifacts, APK install
- Existing Vercel list/delete/deploy infrastructure

## Safety rules respected
- No silent overwrite / auto-delete / auto-move of personal files
- No push without saved association or explicit configure
- Ambiguous roots still use RootSelectionScreen
- Vercel never deletes projects as part of deploy

## Build fix (Gradle wrapper)

The original export was missing `gradle/wrapper/gradle-wrapper.jar`, which caused:

```
Error: Could not find or load main class org.gradle.wrapper.GradleWrapperMain
```

This is fixed: `gradle-wrapper.jar` and `gradlew.bat` are included. Use:

```bash
./gradlew assembleDebug
```

(or open the project in Android Studio). Requires Android SDK / `local.properties` with `sdk.dir=...`.
