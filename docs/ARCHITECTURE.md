# eiNote — Architecture

## Direction
Native Android with Kotlin and Jetpack Compose, local-first persistence.

Recommended stack:
- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- Room
- DataStore
- Android Keystore
- WorkManager
- Android Media APIs
- Android Photo Picker / Storage Access Framework
- Coroutines + Flow
- ViewModel
- Repository pattern

## Principles
UI → ViewModel → Use Case → Repository → Local Data Source.

Organize by feature:
`app/`, `core/`, `feature/notes/`, `feature/tasks/`, `feature/planner/`, `feature/finance/`, `feature/search/`, `feature/backup/`, `feature/settings/`, `feature/security/`.

The local database is the source of truth. Large binary attachments should not be stored directly in normal Room rows; store metadata in the database and content in controlled storage.

Backups must be versioned, validated and optionally encrypted. Restore must not blindly overwrite the current database.

## Security baseline
- No secrets hard-coded in source.
- No API keys committed to Git.
- Least-privilege permissions.
- Prefer Android Photo Picker over broad media permissions.
- Android Keystore for protected keys.
- Optional biometric/PIN app lock.
- Encrypt sensitive exported backups.
- Validate imported backups.
- Never log note content, financial data, recordings or personal attachments.
- Release signing credentials stay outside Git.

## Testing
Unit tests, database tests, ViewModel tests, critical Compose UI tests, backup/restore round-trip tests, and security regression tests.
