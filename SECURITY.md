# Security Policy

## Reporting a vulnerability

**Please don't open a public issue for security problems.**
Use GitHub's private reporting instead: **Security → Report a vulnerability** on this repository.

Include what you found, how to reproduce it, and the app version (*Settings → Updates*).
You'll get an acknowledgement within 7 days. Fixes for confirmed issues are released as soon as possible,
and you'll be credited in the release notes unless you'd rather not be.

## Supported versions

Only the latest release gets security fixes.

## Threat model (summary)

| Threat | Mitigation |
|---|---|
| Someone picks up your unlocked phone | App lock and per-card biometric/PIN prompt. Emulation needs fresh authentication and expires automatically |
| NFC skimming in your pocket | HCE answers `69 82` (security status not satisfied) unless a card is armed. Android also requires the device to be unlocked |
| File-system extraction / rooted device | SQLCipher database. Sensitive columns are also AES-256-GCM encrypted with a non-exportable Android Keystore key (StrongBox when available) |
| Cloud account compromise | Backups are encrypted on the device (Argon2id → AES-256-GCM) before upload. Without your passphrase they're unreadable |
| Shoulder surfing / screen recording | Numbers masked by default. `FLAG_SECURE` blocks screenshots, recording and recents thumbnails |
| Malicious update | Android only installs updates signed with the release key. The app also checks the package name and the published SHA-256 |

### Known limitations
- The field-encryption key isn't bound to biometric authentication, so the NFC service can work while the
  app is unlocked. The UI enforces authentication instead.
- Imported `.pkpass` signatures aren't verified (the content is shown as-is).
- Uses Google Play services / ML Kit (closed source) for Drive sign-in and barcode/OCR scanning.
