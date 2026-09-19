# Project Center

**Project Center** is a mobile-first Android application that turns a ZIP produced by an AI coding agent into an installed APK with the fewest possible steps.

```
AI Agent → ZIP → Project Center → GitHub → Actions → Artifact → APK → Install
```

No Termux. No manual Git commands. No hunting for artifacts in the browser.

## Status: 100% (feature-complete per specification)

All primary phases of the product specification are implemented.

### End-to-end flow

1. Open Project Center  
2. Connect GitHub (OAuth via Custom Tabs + deep link `projectcenter://oauth/callback`)  
3. Select a ZIP (Storage Access Framework)  
4. Choose **Create project** / **Update project** / **Explore ZIP**  
5. Automatic root detection (or pick among candidates)  
6. Confirm (with replace warning when needed)  
7. Push via Git Data API (blobs → tree → commit) with live progress  
8. Success → **Monitor workflow** (live jobs/steps, re-run failed / all)  
9. On failure → important error lines extracted + copy  
10. **Artifacts** → download → extract → detect APK → **Install**  
11. GitHub tab: list, search, create, delete (type-to-confirm), open web  
12. Vercel + GitHub web (WebView + Custom Tabs fallback)  
13. Notifications on workflow success/failure  
14. Activity feed  
15. Self-build GitHub Actions workflow for this app  

## Architecture

```
app/
├── data/
│   ├── github/       # API, ProjectUploader, ArtifactManager, WorkflowMonitor
│   ├── auth/         # OAuth
│   ├── storage/      # ZIP import / safe extract
│   └── activity/
├── domain/models/
├── ui/
│   ├── projects/     # Full ZIP decision + push state machine
│   ├── workflows/    # Status + error log
│   ├── artifacts/
│   ├── github/       # Repos + web
│   ├── vercel/
│   ├── activity/
│   ├── settings/
│   ├── auth/
│   └── components/
├── core/
│   ├── zip/          # Safe extract, root detect, analyzer, error extract
│   ├── apk/
│   ├── security/     # Keystore-backed token store
│   ├── network/
│   └── notifications/
└── MainActivity.kt
```

**Stack:** Kotlin · Jetpack Compose · Material 3 · Coroutines · Retrofit/OkHttp · Moshi · Security-Crypto · Navigation · ViewModel · Browser Custom Tabs

## Build

Requirements:
- JDK 17
- Android SDK 35 (minSdk 26)
- Gradle 8.11+

```bash
cp local.properties.example local.properties
# edit sdk.dir=

# Generate wrapper if needed:
gradle wrapper --gradle-version 8.11.1

./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/`

GitHub Actions (`.github/workflows/build.yml`) builds the same artifact on push to `main`.

## GitHub OAuth setup

1. Create an OAuth App (or GitHub App) at https://github.com/settings/developers  
2. Authorization callback URL: `projectcenter://oauth/callback`  
3. Put the **Client ID** into `app/build.gradle.kts` → `GITHUB_CLIENT_ID`  
4. **Never** embed the Client Secret in the mobile app. Use a small backend proxy or device flow for production token exchange.

Requested scopes: `repo`, `workflow`, `delete_repo`, `read:user`

## Security

- Tokens only in EncryptedSharedPreferences backed by Android Keystore  
- Tokens never logged (OkHttp Authorization header redacted)  
- Zip Slip protection + entry/total size limits  
- `allowBackup="false"` and strict data-extraction rules  

## Limitations

- GitHub and Vercel often block iframes; the app uses WebView + Custom Tabs  
- Very large monorepos may hit GitHub API rate or size limits  
- Silent APK installation is not possible on modern Android (system installer is always shown)  
- OAuth token exchange for public OAuth Apps should go through a backend in production  

## Philosophy

Project Center should feel like a single button between the AI agent and the phone.  
The user should not have to think about Git, Gradle paths, or artifact folders unless they want to.
