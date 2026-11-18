<div align="center">

# CardVault NFC

**Your physical wallet on your phone: NFC tags, transit cards, loyalty cards, tickets and boarding passes.
Biometric-locked, offline-first, and nobody can read your backups but you.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Latest release](https://img.shields.io/github/v/release/dev-uraXium/CardVault)](https://github.com/dev-uraXium/CardVault/releases/latest)
[![Build](https://github.com/dev-uraXium/CardVault/actions/workflows/ci.yml/badge.svg)](https://github.com/dev-uraXium/CardVault/actions/workflows/ci.yml)
![Android 10+](https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white)

**[Website](https://dev-uraxium.github.io/CardVault/)** · **[Download](https://github.com/dev-uraXium/CardVault/releases/latest)**

</div>

---

## Features

**🪪 Every card in one place**
- Scan **NFC** cards and tags, **barcodes / QR codes** with the camera, or enter cards by hand
- **Wallet passes**: boarding passes, event tickets, gift cards. Upcoming passes jump to the top
- Scan a **boarding pass barcode** and the flight, route, seat and booking reference are filled in for you
- Import **Apple Wallet `.pkpass`** files straight from your mail, browser or files

**📡 NFC power tools**
- Phone-as-card: **Host Card Emulation** for NDEF Type 4 tags and custom ISO-DEP cards
- Read **Calypso transit cards** (e.g. MOBIB) without ever writing to them
- Technical inspector: UID, ATQA, SAK, historical bytes, AIDs, NDEF records, hex viewer
- **Live APDU console** to watch reader ↔ phone traffic

**🔒 Private by design**
- Fingerprint / face / PIN lock for the app and per card
- Card data encrypted with **AES-256-GCM** using a hardware-backed key (StrongBox where available), inside a **SQLCipher** database
- NFC emulation is off unless you unlock a card, and auto-expires (15 s – 2 min)
- Screenshots and screen recording blocked
- **Zero-knowledge backups**: encrypted on your phone with your passphrase (Argon2id) before going to a file or *your own* Google Drive

**🎨 Make it yours**
- Card designer: colours, gradients, icons, your own artwork, custom fields
- App themes: light / dark / system, accent colours, Material You, pure-black AMOLED

## Install

1. Download the latest `cardvault-nfc-x.y.z.apk` from **[Releases](https://github.com/dev-uraXium/CardVault/releases/latest)**.
2. Open it on your phone and allow installing from that app when Android asks.
3. Updates are offered inside the app (*Settings → Updates*).

**Verify your download.** Every release is signed with the same key. Its certificate SHA-256 fingerprint is:

```
49:B6:1A:F3:6B:D5:B1:9C:E1:B5:78:60:D7:53:E7:9A:0F:C8:C4:02:F2:AA:19:1D:05:9E:06:54:F0:7C:E0:C7
```

Check it with `apksigner verify --print-certs cardvault-nfc-x.y.z.apk`. Each release also lists the SHA-256 of the APK.

## What it can't do (honestly)

- **No payment cards.** Bank cards (EMV) are detected and refused.
- **Transit cards are read-only.** Gates check a cryptographic signature from the card's secure chip,
  which no unrooted phone can reproduce. CardVault shows your card's data but can't replace it at the gate.
- **MIFARE Classic / hotel keys / UID-only badges** can't be emulated: Android randomises the emulated UID
  and doesn't support Crypto1. They're stored as reference cards.
- **Loyalty cards** work via barcode. Hardly any shop reads loyalty cards over NFC.

## Privacy

No accounts, ads, analytics or trackers. The app only goes online when **you** back up to Google Drive, and
to check GitHub for updates (you can turn that off). See [SECURITY.md](SECURITY.md) for the threat model.

## Build from source

Requirements: Android Studio (latest stable) or JDK 17 plus the Android SDK.

```bash
git clone https://github.com/dev-uraXium/CardVault.git
cd CardVault
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Google Drive backup additionally needs your own OAuth client.
The steps are in [docs/google-drive.md](docs/google-drive.md).

**Stack:** Kotlin · Jetpack Compose · Material 3 · Room + SQLCipher · Android Keystore · CameraX + ML Kit · ZXing · BouncyCastle (Argon2)

## Contributing

Bug reports, ideas and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first.
**Security issues:** don't open a public issue. Follow [SECURITY.md](SECURITY.md).

## License

Copyright © 2026 uraXium

CardVault NFC is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License v3.0 or later**. It's distributed without any warranty. See [LICENSE](LICENSE).

Anyone who distributes a modified version must also publish its source code under the same license.

<sub>MOBIB, Apple Wallet, Google Wallet, Google Drive and other names are trademarks of their respective owners.
CardVault NFC is an independent project and isn't affiliated with or endorsed by them.</sub>
