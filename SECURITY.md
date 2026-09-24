# Security Policy

## Security goals
eiNote may contain private notes, finances, photos, recordings and other personal information.

Requirements:
- offline-first by default
- least-privilege permissions
- Android Keystore for protected cryptographic keys
- encrypted sensitive exports/backups
- no credentials or signing keys in Git
- no sensitive user content in logs
- safe import validation
- secure release signing

## Reporting a vulnerability
Do not publish sensitive vulnerability details in a public issue. Use GitHub's private security reporting mechanism when available.

## Scope
Review local database, attachment storage, backup/restore, encryption/key management, app lock, exported files, Android permissions, release configuration and third-party dependencies.
