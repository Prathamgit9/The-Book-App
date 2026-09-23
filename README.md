# The Book App

A personal Android reading journal built around the feeling of an old hardcover book: quiet, literary, and useful.

## Included
- Home, Library, Lists, Stats and More.
- Open Library search with covers, ISBN, publisher, year and subjects.
- Reading, Up Next, TBR, On Hold, Completed and DNF.
- Book detail pages with progress, dates, notes and ratings.
- Live reading sessions with automatic history.
- Custom lists and list membership.
- Reading goals and six-month page chart.
- JSON backup export/import.
- Local-first persistence.
- GitHub Actions build + tagged release workflow.

## Build
Use JDK 17 and Gradle 8.9:
\`\`\`
gradle assembleDebug
\`\`\`

## Release
Push a \`v*\` tag to publish a GitHub Release with the APK.

Release APKs are named `The-Book-App-vX.Y.Z.apk`. The in-app updater checks the latest GitHub Release, downloads the APK, and hands it to Android's package installer. Android requires user confirmation and permission to install from this source.

For production updates, all distributed APKs must be signed with the same private release key. The release workflow is ready for these GitHub Actions secrets:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The current `v0.2.0` build is still debug-signed, so it should be replaced with a release-signed build before relying on in-place updates.

## Version 0.2.0

Complete first-pass feature build; tagged releases are created automatically from the release workflow.
