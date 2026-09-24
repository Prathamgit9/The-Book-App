# Changelog

## [0.2.1] — 2026-09-24

### Added
- Broader book discovery using Open Library ISBN/edition lookup and Google Books fallback.
- Search support for books that are missing from the original Open Library search results.
- Expanded edition metadata during book search.
- Branded adaptive launcher icon using The Book App's book, arch and leaf mark.
- GitHub Release update checker and APK installer flow.

### Release engineering
- Version code increased to 3.
- Release builds are intended to use the permanent Android production signing key.
- Future releases must keep the same application ID and signing certificate so in-place updates work without clearing local data.

## [0.2.0] — 2026-09-23

### Added
- Full local-first reading journal flow.
- Home with currently reading, Up Next, recent activity and shelf summary.
- Library search, status filters, sorting and grid/list views.
- Book detail pages with progress, status, dates, notes, rating and reading history.
- Reading sessions with a live timer, page tracking, session notes, pause/save and finish-book flow.
- Automatic reading-session history with pages and minutes.
- System lists: Reading, Up Next, To Read, On Hold, Completed and DNF.
- Custom lists with descriptions and per-book membership toggles.
- Reading statistics including reading days, minutes and a six-month page chart.
- Yearly reading goal.
- Paper / Ink appearance switch.
- JSON backup export and import.
- Persistence migration from the original shelf format.
- Refined literary color palette, typography and book-first layout.
