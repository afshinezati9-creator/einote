# eiNote — AI Developer / Codebase Guide

> نسخه مستند: 2026-09-24  
> مخزن: `afshinezati9-creator/einote`  
> شاخه اصلی: `main`  
> وضعیت مبنا: commit `c1197e416e13955d53d117449105207fa9c6138a`  
> نسخه برنامه: `1.0.0` — versionCode `9`

## 1. این فایل برای چیست؟

این سند برای توسعه‌دهندگان انسانی و مخصوصاً AI Coding Agentها نوشته شده تا قبل از تغییر کد، تصویر دقیقی از eiNote داشته باشند.

AI نباید با دیدن یک فایل، ساختار برنامه را حدس بزند. ابتدا این سند، سپس فایل‌های مرتبط و در نهایت وابستگی‌های آن‌ها را بررسی کند.

اصل مهم پروژه:

> eiNote یک «دفتر سفید دیجیتال» است؛ کاربر باید بتواند هر نوع اطلاعات شخصی را در یک محیط ساده و یکپارچه ثبت، مدیریت، جستجو، برنامه‌ریزی و نگهداری کند.

پروژه Android Native است و برای کار آفلاین طراحی شده است.

---

# 2. معرفی محصول

eiNote یک دفترچه شخصی / life organizer است که چند حوزه را در یک برنامه جمع می‌کند:

- یادداشت‌نویسی
- ویرایش متن غنی
- متن فارسی و انگلیسی در یک محیط
- راست‌چین، چپ‌چین و وسط‌چین
- Bold / Italic / Underline / Strike
- چک‌لیست
- bullet
- تصویر
- صوت
- فایل و attachment
- یادداشت مهم / Pin
- آرشیو
- تگ
- جستجو
- فیلتر
- برنامه‌ریزی
- موعد انجام کار
- یادآوری
- حساب‌وکتاب شخصی
- درآمد
- هزینه
- تراکنش مالی
- پشتیبان‌گیری
- بازیابی
- backup رمزنگاری‌شده
- PIN و امنیت
- تنظیمات ظاهری
- تراکم داشبورد
- انیمیشن
- فرمت فارسی و تومان
- معماری Offline-first

سه فضای اصلی داشبورد:

1. یادداشت من
2. حساب‌کتاب
3. برنامه‌ریزی

این سه بخش در UI از هم تفکیک شده‌اند و نباید کنترل‌های مالی یا برنامه‌ریزی بدون دلیل وارد ویرایشگر یادداشت شوند.

---

# 3. فناوری‌ها و Stack

## زبان و پلتفرم

- Kotlin
- Android Native
- Jetpack Compose
- Material 3
- Android SDK 35
- minSdk 26
- targetSdk 35
- Java 17
- Kotlin JVM Toolchain 17

## Build

- Gradle
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Kotlin Compose Plugin 2.0.21
- KSP 2.0.21-1.0.28

## UI

- Jetpack Compose
- Material 3
- Material Icons Extended
- Navigation Compose
- Coil Compose

## Architecture / lifecycle

- ViewModel
- StateFlow / Flow
- Repository pattern
- Coroutines
- Room

## Database

- Room 2.7.0
- SQLite زیرساخت Room
- database name: `einote.db`
- current schema version: 8

## Background

- WorkManager
- ReminderWorker برای کارهای یادآوری

## Security

- Android Keystore
- Android Biometric
- PBKDF2
- AES/GCM
- encrypted backup
- constant-time comparison

## CI/CD

- GitHub Actions
- Android SDK setup
- JDK 17
- Gradle build
- unit tests
- instrumentation tests
- emulator
- APK artifact

---

# 4. معماری کلی

جریان اصلی داده:

UI
↓
ViewModel
↓
Repository
↓
Room DAO
↓
SQLite

برای backup:

UI
↓
BackupViewModel
↓
BackupManager
↓
Room + attachment files
↓
Backup file

برای backup رمزنگاری‌شده:

BackupManager
↓
BackupCrypto
↓
AES/GCM + PBKDF2

برای امنیت:

UI
↓
SecurityViewModel
↓
SecurityManager
↓
Android Keystore / local secure state

---

# 5. مدل داده

## NoteEntity

نماینده خود «یادداشت» است.

اطلاعات اصلی مانند:

- id
- title
- timestamps
- tags
- pinned
- archived
- space
- color

نکته: محتوای اصلی rich text داخل خود NoteEntity ذخیره نمی‌شود؛ محتوای قابل ویرایش به صورت block نگهداری می‌شود.

## NoteBlockEntity

واحد اصلی محتوای داخل یک یادداشت است.

Block می‌تواند برای موارد مختلف استفاده شود:

- writing
- checklist
- bullet
- image
- audio
- سایر انواع تعریف‌شده در BlockType

ویژگی‌های مهم:

- noteId
- type
- content
- position
- dueAt
- reminderAt
- completedAt
- textColor
- textSizeSp
- alignment
- timestamps

`alignment` در schema version 8 اضافه شده و مقادیر مفهومی آن:

- auto
- left
- center
- right

## FinanceTransactionEntity

تراکنش مالی شخصی.

اطلاعات اصلی:

- title
- amountToman
- type
- category
- transactionAt
- note
- createdAt

مبلغ در تومان نگهداری می‌شود.

## AttachmentEntity

فقط metadata فایل را در Room نگهداری می‌کند.

شامل:

- noteId
- fileName
- mimeType
- sizeBytes
- localPath
- createdAt

خود binary فایل داخل Room ذخیره نمی‌شود.

---

# 6. ساختار کامل فایل‌ها

## Root

### `README.md`

معرفی عمومی پروژه.

شامل:

- معرفی eiNote
- هدف برنامه
- قابلیت‌های اصلی
- اشاره به offline-first
- اشاره به backup
- امنیت
- GitHub Actions

این فایل برای آشنایی سریع است، نه مرجع معماری عمیق.

### `SECURITY.md`

قواعد امنیتی پروژه.

برای تغییرات مربوط به:

- PIN
- encryption
- backup
- secrets
- logging
- signing

باید بررسی شود.

### `.gitignore`

فایل‌ها و خروجی‌هایی که نباید وارد Git شوند.

---

# 7. Build و Gradle

## `build.gradle.kts`

نسخه pluginهای اصلی پروژه را تعریف می‌کند:

- Android Application Plugin
- Kotlin Android
- Kotlin Compose
- KSP

نسخه‌های فعلی:

- AGP 8.7.3
- Kotlin 2.0.21
- KSP 2.0.21-1.0.28

AI هنگام تغییر نسخه‌ها نباید فقط یک dependency را تغییر دهد؛ compatibility بین AGP / Kotlin / Compose / KSP باید بررسی شود.

## `settings.gradle.kts`

ساختار Gradle پروژه را تعریف می‌کند.

- repositoryها
- rootProjectName = eiNote
- include(":app")

## `gradle.properties`

تنظیمات عمومی Gradle پروژه.

## `app/build.gradle.kts`

تعریف ماژول Android.

موارد اصلی:

- applicationId = `com.einote.app`
- compileSdk = 35
- minSdk = 26
- targetSdk = 35
- Java 17
- Kotlin JVM 17
- versionCode = 9
- versionName = 1.0.0

Dependencyهای اصلی:

- Compose
- Material 3
- Navigation
- Lifecycle
- Room
- KSP
- WorkManager
- Biometric
- Coil
- JUnit
- AndroidX Test

اگر خطای build یا dependency ایجاد شد، این فایل اولین محل بررسی است.

---

# 8. Android Manifest و Resources

## `app/src/main/AndroidManifest.xml`

تعریف ساختار Android app، application و Activityها/تنظیمات manifest.

## `app/src/main/res/drawable/ic_einote.xml`

آیکون/vector مربوط به eiNote.

## `app/src/main/res/values/strings.xml`

رشته‌های پایه Android.

## `app/src/main/res/values-fa/strings.xml`

رشته‌های فارسی.

## `app/src/main/res/values/styles.xml`

styleهای XML موردنیاز Android.

## `app/src/main/res/xml/locales_config.xml`

تنظیم زبان‌های برنامه.

---

# 9. مهم‌ترین فایل UI

## `app/src/main/java/com/einote/app/MainActivity.kt`

این بزرگ‌ترین فایل فعلی پروژه است و بخش زیادی از UI و presentation layer را در خود دارد.

مسئولیت‌های فعلی آن شامل:

### Shell برنامه

- Main Activity
- navigation/state
- dashboard
- top app bar
- FAB
- tabهای اصلی

### Dashboard

سه tab:

- یادداشت من
- حساب‌کتاب
- برنامه‌ریزی

همچنین:

- search
- filter
- dashboard spacing
- animated tabs
- empty states
- recent items
- finance summary
- planner summary

### Notes UI

- لیست یادداشت‌ها
- ساخت یادداشت
- ویرایش یادداشت
- pin
- archive
- tag/filter
- search
- empty state

### Rich Editor

ویرایشگر یکپارچه متن.

پشتیبانی فعلی:

- Bold
- Italic
- Underline
- Strikethrough
- Left
- Center
- Right
- Auto direction
- Persian RTL
- English LTR
- ترکیب فارسی و انگلیسی
- تبدیل متن قدیمی/plain text
- ذخیره محتوای styled به ساختار HTML-like

توابع/کامپوننت‌های مهم مرتبط با editor:

- InlineStyle
- RichTextToolbar
- RichFormatButton
- AlignmentButton
- RichEditorField
- richTextToFieldValue
- spannedToAnnotatedString
- fieldValueToHtml
- mergeSpanStyles
- rangeHasInlineStyle
- toggleInlineStyle

### Block UI

نمایش و ویرایش blockهای:

- writing
- checklist
- bullet
- image
- audio

### Finance UI

- balance
- income
- expense
- transactions
- finance dashboard
- finance screen

### Planner UI

- progress
- upcoming planned blocks
- due items
- completion state

### Backup/Security/Settings UI

این فایل presentation و screenهای مربوط به:

- backup
- restore
- encrypted backup
- password dialogs
- security
- settings
- customization

را نیز به هم متصل می‌کند.

### نکته مهم برای AI

`MainActivity.kt` بسیار بزرگ است.

قبل از refactor کردن آن، هیچ بخش UI را به صورت حدسی جابه‌جا نکن.

هر extraction باید:

1. dependencyهای Compose را بررسی کند.
2. state ownership را مشخص کند.
3. callbackها را حفظ کند.
4. behavior فعلی را تغییر ندهد.
5. بعد از آن build/test شود.

---

# 10. Data Layer

مسیر:

`app/src/main/java/com/einote/app/data/`

## `AttachmentDao.kt`

DAO مربوط به attachmentها.

کارهایی مانند:

- observe attachmentهای یک note
- حذف attachmentها
- عملیات database روی metadata فایل

## `AttachmentEntity.kt`

مدل Room برای metadata فایل.

Binary فایل اینجا نیست.

## `BlockType.kt`

نوع blockهای note را تعریف می‌کند.

این فایل مرجع نوع محتوای block است.

قبل از اضافه کردن block جدید، این فایل و نحوه render شدن block در MainActivity بررسی شود.

## `FinanceTransactionDao.kt`

DAO تراکنش‌های مالی.

مسئول:

- observe
- insert
- update/delete
- queryهای مالی

## `FinanceTransactionEntity.kt`

Entity جدول مالی.

مبلغ با `amountToman` ذخیره می‌شود.

## `NoteBlockDao.kt`

DAO محتوای blockها.

مسئولیت‌های مهم:

- observe blockهای یک note
- position
- nextPosition
- shiftPositions
- update block
- delete block
- planned blocks
- زمان‌بندی/موعد blockها

## `NoteBlockEntity.kt`

مدل داده block.

در حال حاضر دارای ویژگی‌های محتوایی و presentation مثل:

- type
- content
- position
- textColor
- textSizeSp
- alignment
- dueAt
- reminderAt
- completedAt

است.

## `NoteDao.kt`

DAO اصلی Note.

مسئول:

- observe notes
- search
- filter
- insert
- update
- delete
- pin
- archive
- tag source query

Phase performance یک query مستقیم برای tag source اضافه کرده تا tags از کل لیست noteها دوباره محاسبه نشوند.

## `NoteDatabase.kt`

مرکز Room database.

Entityها:

- NoteEntity
- NoteBlockEntity
- FinanceTransactionEntity
- AttachmentEntity

version فعلی:

`8`

مهاجرت‌های مهم:

- 2→3: reminder/due/completed
- 3→4: finance
- 4→5: attachments
- 5→6: text color/size
- 6→7: space/color برای note
- 7→8: alignment برای block

هیچ migration موجود نباید حذف یا بازنویسی شود مگر اینکه استراتژی migration به صورت آگاهانه تغییر کند.

## `NoteEntity.kt`

Entity اصلی note.

metadata مربوط به خود یادداشت در این فایل است.

محتوای چندبخشی داخل NoteBlockEntity قرار دارد.

## `NoteRepository.kt`

لایه واسط بین ViewModel و DAOها.

مسئول:

- مشاهده notes
- مشاهده tag sources
- مشاهده blocks
- مشاهده planned blocks
- مشاهده attachments
- insert/update/delete
- pin/archive
- ایجاد block
- درج block در position مشخص
- جابه‌جایی block
- update block
- delete block

ViewModel نباید مستقیماً منطق پیچیده Room را مدیریت کند؛ Repository نقطه ورود data layer است.

---

# 11. UI ViewModelها

مسیر:

`app/src/main/java/com/einote/app/ui/`

## `NoteViewModel.kt`

مرکز state و عملیات Notes.

مسئول:

- current space
- search query
- note list
- tags
- filters
- archived
- pinned
- sorting
- create/update/delete
- blocks
- autosave
- planned blocks
- normalization فارسی برای search/tag

نکته performance:

لیست tagها از query مستقیم DAO به نام `observeTagSources` تغذیه می‌شود.

## `FinanceViewModel.kt`

State و عملیات حساب‌وکتاب.

مسئول:

- مشاهده تراکنش‌ها
- ثبت تراکنش
- محاسبه/ارائه مجموع‌ها
- income
- expense
- finance state

منطق UI مالی نباید وارد NoteViewModel شود.

## `AttachmentViewModel.kt`

مدیریت فایل‌ها و attachmentها.

مسئول:

- انتخاب/افزودن فایل
- metadata
- نگهداری local file
- ارتباط attachment با note
- مدیریت lifecycle فایل‌ها

## `BackupViewModel.kt`

State و عملیات backup/restore.

مسئول:

- export
- import
- encrypted export
- encrypted import
- password state
- خطا/موفقیت عملیات
- پاک‌سازی password از حافظه تا حد ممکن

## `SecurityViewModel.kt`

Bridge بین UI امنیت و SecurityManager.

مسئول:

- PIN state
- verify
- set/reset
- lock state
- failed attempts
- lockout state

## `SettingsViewModel.kt`

State تنظیمات را در اختیار Compose می‌گذارد.

شامل:

- dashboard density
- animations
- سایر تنظیمات UI

---

# 12. Settings

## `app/src/main/java/com/einote/app/settings/SettingsManager.kt`

لایه persistence تنظیمات کاربر.

تنظیمات فعلی مهم:

- dashboard density
- animations

Densityهای dashboard:

- airy
- balanced
- compact

این فایل محل تعریف keyها و خواندن/نوشتن preferenceهاست.

## `app/src/main/java/com/einote/app/ui/SettingsViewModel.kt`

API reactive برای UI تنظیمات.

UI نباید مستقیماً با persistence settings کار کند.

---

# 13. Theme

## `app/src/main/java/com/einote/app/ui/Theme.kt`

Theme و Material configuration برنامه.

برای تغییر:

- رنگ
- typography
- Material theme
- dark/light behavior

ابتدا این فایل بررسی شود.

---

# 14. Persian utilities

## `app/src/main/java/com/einote/app/util/PersianFormat.kt`

توابع عمومی مربوط به نمایش فارسی.

زمینه‌های اصلی:

- Persian digits
- number formatting
- تومان
- تاریخ/نمایش فارسی
- normalization متن فارسی

این فایل باید محل مشترک formatting باشد تا منطق تبدیل عدد/متن در چند screen تکرار نشود.

## `PersianFormatTest.kt`

Unit test مربوط به utilityهای فارسی.

مسیر:

`app/src/test/java/com/einote/app/util/PersianFormatTest.kt`

---

# 15. Reminder

## `ReminderWorker.kt`

Worker مربوط به عملیات reminder.

با WorkManager اجرا می‌شود.

برای تغییر رفتار notification/reminder:

1. این فایل
2. NoteBlockEntity
3. NoteBlockDao
4. منطق زمان‌بندی در UI/ViewModel

باید با هم بررسی شوند.

---

# 16. Security

مسیر:

`app/src/main/java/com/einote/app/security/`

## `SecurityManager.kt`

منطق امنیت محلی.

ویژگی‌های مهم:

- PIN verification
- PBKDF2
- Android Keystore backed encryption
- constant-time comparison
- failed-attempt counter
- lockout

سیاست فعلی:

- بعد از 5 تلاش ناموفق، lockout حدود 30 ثانیه فعال می‌شود.
- موفقیت، failed attempts را reset می‌کند.

AI نباید الگوریتم امنیتی را صرفاً برای «ساده‌تر شدن کد» با hashing ساده یا plaintext جایگزین کند.

---

# 17. Backup

مسیر:

`app/src/main/java/com/einote/app/backup/`

## `BackupManager.kt`

یکی از فایل‌های اصلی پروژه است.

مسئول:

- export database data
- import
- notes
- blocks
- finance
- attachments metadata
- attachment binaries
- manifest
- schema version
- validation
- temporary files
- encrypted backup workflow

Backup باید بتواند ساختار داده برنامه را بدون وابستگی به UI منتقل کند.

schema فعلی backup:

`8`

در restore برای فیلدهای جدید باید default امن تعریف شود تا backup قدیمی باعث crash نشود.

## `BackupCrypto.kt`

رمزنگاری backup.

الگوریتم فعلی:

- AES/GCM/NoPadding
- PBKDF2WithHmacSHA256
- 210000 iterations
- salt تصادفی 16 byte
- IV تصادفی 12 byte
- حداقل password: 8 کاراکتر
- magic header: `EINOTE-ENC-1`

هدف:

- محرمانگی
- integrity/authentication مربوط به GCM
- تشخیص password اشتباه
- تشخیص فایل دستکاری‌شده
- cleanup فایل موقت

قبل از تغییر crypto، compatibility فایل‌های قبلی باید در نظر گرفته شود.

---

# 18. Tests

## `app/src/test/java/com/einote/app/util/PersianFormatTest.kt`

Unit test utilityهای PersianFormat.

## `app/src/androidTest/java/com/einote/app/data/NoteDaoTest.kt`

Instrumented database/DAO test.

برای رفتار واقعی Room و queryها استفاده می‌شود.

## `app/src/androidTest/java/com/einote/app/data/NoteDatabaseMigrationTest.kt`

مهم‌ترین تست migration.

هدف:

- بررسی schema
- migrationها
- version 8
- وجود columnهای جدید
- compatibility مسیر migration

هر تغییری در Entity یا Database باید این تست را در نظر بگیرد.

---

# 19. GitHub Actions

## `.github/workflows/android.yml`

CI اصلی Android.

مراحل کلی:

1. checkout
2. JDK 17
3. Gradle 8.9
4. آماده‌سازی Android SDK
5. unit tests
6. debug APK build
7. instrumentation test compilation
8. KVM
9. emulator
10. upload APK artifact

این workflow برای جلوگیری از شکستن build در تغییرات آینده است.

نکته: CI در حال حاضر بخشی از فرآیند release/debug است.

---

# 20. قراردادهای مهم برای AI Developer

## قبل از تغییر

AI باید:

1. فایل موردنظر را از `main` بخواند.
2. dependency آن را پیدا کند.
3. Entity/DAO/Repository/ViewModel/UI مرتبط را بررسی کند.
4. migration موردنیاز را بررسی کند.
5. تست‌های مرتبط را پیدا کند.

## هنگام تغییر

AI نباید:

- فایل جدیدی بسازد که وظیفه فایل موجود را دوباره انجام دهد.
- database schema را بدون migration تغییر دهد.
- binary attachment را وارد Room کند.
- منطق Finance را وارد NoteViewModel کند.
- منطق Planner را با Note editor قاطی کند.
- strings و formatting فارسی را در چند جای مختلف کپی کند.
- encryption را با روش ساده‌تر جایگزین کند.
- UI را بدون حفظ state فعلی refactor کند.
- dependency جدید را بدون بررسی نیاز واقعی اضافه کند.

## اصل معماری

اگر قابلیت جدید مربوط به داده است:

Entity → DAO → Repository → ViewModel → UI

اگر قابلیت فقط UI است:

Composable / MainActivity → state موجود

اگر قابلیت امنیتی است:

UI → ViewModel → SecurityManager

اگر قابلیت backup است:

UI → BackupViewModel → BackupManager → BackupCrypto

---

# 21. قوانین تغییر Database

هر تغییر Entity که schema را تغییر دهد باید شامل:

1. افزایش database version
2. migration جدید
3. اصلاح Entity
4. اصلاح DAO در صورت نیاز
5. اصلاح Repository
6. اصلاح BackupManager
7. اصلاح restore defaults
8. اصلاح Migration Test

باشد.

هیچ‌وقت صرفاً version را بالا نبرید و migration را حذف نکنید.

---

# 22. Rich Text Contract

محتوای writing block می‌تواند styled باشد.

Editor فعلی از تبدیل بین:

- Compose text state
- AnnotatedString / spans
- HTML-like storage

استفاده می‌کند.

پشتیبانی فعلی:

- bold
- italic
- underline
- strike
- alignment
- RTL/LTR content direction

نکته مهم:

alignment فعلی در سطح block است، نه paragraph-run مستقل داخل یک block.

بنابراین اگر در آینده نیاز به این رفتار باشد:

«یک پاراگراف فارسی راست‌چین، پاراگراف بعدی انگلیسی چپ‌چین، و ادامه همان block»

باید مدل rich text به paragraph/run structure ارتقا داده شود یا blockها به صورت هوشمند split شوند.

AI نباید بدون تصمیم معماری، این محدودیت را با hack حل کند.

---

# 23. Search و Persian normalization

جستجو باید با تفاوت‌های رایج فارسی سازگار باشد.

نمونه normalization فعلی:

- `ي` → `ی`
- `ك` → `ک`

در تغییر search/tag، ابتدا utility و منطق موجود NoteViewModel را بررسی کنید تا normalizationهای جدید باعث ناسازگاری نشوند.

---

# 24. Dashboard Contract

Dashboard سه tab دارد:

### یادداشت من

تمرکز:

- notes
- search
- tags
- pinned
- archived
- recent notes
- create note

### حساب‌کتاب

تمرکز:

- balance
- income
- expense
- transactions

### برنامه‌ریزی

تمرکز:

- progress
- upcoming items
- due items
- completion

کنترل‌های این سه حوزه نباید بدون نیاز وارد یکدیگر شوند.

---

# 25. UI Philosophy

هدف UI:

> Minimal + Modern + Calm + Useful

نه:

> Empty + Boring

و نه:

> Crowded + Card-heavy

ویژگی‌های طراحی:

- rounded surfaces
- subtle elevation
- animation محدود و هدفمند
- empty state جذاب
- spacing قابل تنظیم
- dashboard density
- tab animation
- RTL-first
- Persian typography

کاربر نباید احساس کند هر خط متن یا هر block داخل یک card جداگانه گیر افتاده است.

---

# 26. وضعیت فعلی قابلیت‌ها

## کامل/پیاده‌سازی‌شده

- Android Native
- Compose
- Room
- Notes
- blocks
- audio
- image
- rich text
- RTL/LTR
- alignment
- dashboard
- finance dashboard
- planner dashboard
- backup
- restore
- encrypted backup
- PIN/security
- customization
- search
- tags
- filters
- performance optimization
- CI/CD
- release version 1.0.0 configuration

## در مسیر آینده

- cloud sync
- multi-device sync
- قابلیت‌های پیشرفته‌تر finance
- planner پیشرفته‌تر
- reminderهای گسترده‌تر
- search پیشرفته‌تر
- attachment management پیشرفته‌تر
- قابلیت‌های AI داخل خود محصول

Cloud/sync نباید با معماری فعلی Offline-first در تضاد قرار بگیرد.

---

# 27. نقشه dependencyهای اصلی

```
MainActivity
 ├── NoteViewModel
 │    └── NoteRepository
 │         ├── NoteDao
 │         ├── NoteBlockDao
 │         └── AttachmentDao
 │
 ├── FinanceViewModel
 │    └── FinanceTransactionDao
 │
 ├── BackupViewModel
 │    └── BackupManager
 │         └── BackupCrypto
 │
 ├── SecurityViewModel
 │    └── SecurityManager
 │
 └── SettingsViewModel
      └── SettingsManager

NoteDatabase
 ├── NoteEntity
 ├── NoteBlockEntity
 ├── FinanceTransactionEntity
 └── AttachmentEntity
```

---

# 28. اگر AI بخواهد قابلیت جدید اضافه کند

مثلاً «یادداشت صوتی جدید»:

1. `BlockType.kt` را بررسی کن.
2. `NoteBlockEntity.kt` را بررسی کن.
3. `NoteBlockDao.kt` را بررسی کن.
4. `NoteRepository.kt` را بررسی کن.
5. `AttachmentViewModel.kt` را بررسی کن.
6. render آن را در `MainActivity.kt` پیدا کن.
7. اگر schema تغییر نکرد، migration نساز.
8. اگر schema تغییر کرد، migration + test + backup را هم تغییر بده.
9. فقط در صورت نیاز dependency جدید اضافه کن.

مثلاً «فیلتر مالی جدید»:

1. FinanceTransactionEntity
2. FinanceTransactionDao
3. FinanceViewModel
4. MainActivity finance UI
5. test مربوط به query

مثلاً «تنظیم جدید»:

1. SettingsManager
2. SettingsViewModel
3. MainActivity / Settings UI

---

# 29. Release و Versioning

نسخه فعلی:

```
versionName = 1.0.0
versionCode = 9
```

قبل از release:

- compile
- unit tests
- instrumentation tests
- migration tests
- debug/release build
- APK artifact
- بررسی manifest
- بررسی versionCode/versionName

نصب نهایی روی دستگاه/Emulator باید در مرحله نهایی انجام شود، نه برای هر تغییر کوچک.

---

# 30. مهم‌ترین فایل‌ها برای شروع مطالعه

اگر AI تازه وارد پروژه شده، این ترتیب پیشنهاد می‌شود:

1. `README.md`
2. این فایل
3. `docs/PRODUCT.md`
4. `docs/ARCHITECTURE.md`
5. `app/build.gradle.kts`
6. `NoteDatabase.kt`
7. Entityها
8. DAOها
9. `NoteRepository.kt`
10. ViewModelها
11. `MainActivity.kt`
12. Backup/Security
13. Tests
14. GitHub Actions

---

# 31. اصل نهایی پروژه

eiNote قرار نیست فقط یک Note App ساده باشد.

مدل ذهنی صحیح:

**Notebook + Personal Organizer + Planner + Personal Finance + Secure Local Archive**

همه این قابلیت‌ها باید در عین توسعه‌پذیری، یک تجربه کاربری یکپارچه داشته باشند.

هر تغییر جدید باید این چهار سؤال را پاسخ دهد:

1. این قابلیت متعلق به کدام domain است؟
2. داده آن کجا نگهداری می‌شود؟
3. state آن توسط کدام ViewModel کنترل می‌شود؟
4. UI آن چگونه بدون شلوغ کردن تجربه اصلی وارد برنامه می‌شود؟

اگر پاسخ این چهار سؤال روشن نیست، قبل از کدنویسی باید معماری قابلیت مشخص شود.

---

## خلاصه یک‌خطی برای AI

**eiNote یک Android Native، Kotlin، Jetpack Compose، Room و Offline-first personal notebook است که Notes، Rich Text، Tasks/Planning، Personal Finance، Attachments، Search/Tags، Backup/Restore، Encryption، PIN/Security و Customization را در یک معماری Repository + ViewModel + Room ارائه می‌کند؛ قبل از هر تغییر، dependency chain و migration/backup compatibility را بررسی کن.**
