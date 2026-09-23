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

The current release APK is debug-signed for development testing. A production keystore is required before distributing signed update builds.

## Version 0.2.0

Complete first-pass feature build; tagged releases are created automatically from the release workflow.
