# Contributing to CardVault NFC

Thanks for helping! A few ground rules keep things smooth.

## Before you start
- For anything bigger than a small fix, **open an issue first** so we can agree on the approach.
- Security bugs go through [SECURITY.md](SECURITY.md), not public issues.

## Development
- Use Android Studio (latest stable) and JDK 17.
- Run `./gradlew testDebugUnitTest assembleDebug` before opening a PR. CI runs the same.
- Kotlin official code style. Compose for all UI.
- Keep the security properties intact:
  - never log card numbers, APDU payloads or passphrases
  - store sensitive fields only through `CardRepository` (encrypted columns)
  - nothing may make network calls except the Drive backup and the update check
- Transit parsers must stay **read-only**: no secure sessions, no writes to cards.
- Add unit tests for parsers and protocol code (see `app/src/test`).

## Pull requests
- One topic per PR, with a clear description and screenshots for UI changes.
- Make sure CI is green.

## License of contributions
By submitting a contribution you agree it's licensed under the project's license,
**GPL-3.0-or-later** (inbound = outbound). Only submit code you have the right to contribute.
Don't copy code from projects with incompatible licenses.
