# Third-party software

CardVault NFC uses these libraries. Each keeps its own license.

| Component | License |
|---|---|
| AndroidX / Jetpack Compose / Room / CameraX / Biometric | Apache-2.0 |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| SQLCipher for Android (Community Edition) | BSD-style (Zetetic) |
| ZXing core | Apache-2.0 |
| Bouncy Castle (Argon2) | MIT-style (Bouncy Castle License) |
| Google ML Kit (barcode scanning, text recognition) | Proprietary, Google APIs Terms of Service |
| Google Play services Auth | Proprietary, Google APIs Terms of Service |

The proprietary Google components are runtime libraries and not part of CardVault's source. A build flavour
without them (for F-Droid) is planned.
