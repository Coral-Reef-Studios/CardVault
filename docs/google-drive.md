# Google Drive backup setup (self-built APKs)

Official releases are already set up. If you build CardVault yourself, Google has to know your app's signing key:

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project and **enable the Google Drive API**.
2. **OAuth consent screen**: choose External, add the scope `https://www.googleapis.com/auth/drive.appdata`, and add yourself as a test user.
3. **Credentials → Create OAuth client ID → Android**:
   - Package name: `com.cardvault.nfc` (or your own applicationId)
   - SHA-1: run `./gradlew signingReport` and copy the SHA-1 of the variant you install

No client ID goes into the code. Google Play services matches the package name and signing certificate.
Backups go to the hidden *appDataFolder*, which only CardVault can see.
