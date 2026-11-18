# Changelog

All notable changes to CardVault NFC. Format: [Keep a Changelog](https://keepachangelog.com), versions follow SemVer.

## [0.2.0] - 2026-11-18
### Added
- Wallet passes: boarding passes, event tickets and gift cards with wallet-style cards; upcoming passes sorted first.
- Boarding pass barcode (IATA BCBP) auto-fill from a camera scan.
- Apple Wallet `.pkpass` import.
- App theme: system/light/dark, accent colour, Material You, pure-black AMOLED, compact wallet list.
- In-app updates from GitHub Releases (checksum + package verification).

## [0.1.0] - 2026-11-18
### Added
- Biometric-locked vault with hardware-backed AES-256-GCM field encryption and SQLCipher database.
- NFC reader (NFC-A/B/F/V, NDEF, ISO-DEP), technical inspector and read-only Calypso/MOBIB reader.
- Host Card Emulation for NDEF Type 4 tags and custom ISO-DEP APDU tables with a timed emulation window.
- Camera barcode + card-number scanning, high-brightness barcode display.
- Card customisation studio and custom fields.
- Zero-knowledge encrypted backups (Argon2id + AES-256-GCM) to a file or Google Drive.
