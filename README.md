# DentTrack – dental storage tracking (Android tablet)

A BoxHero-style stock app for dental clinics. Kotlin, Jetpack Compose (Material 3), local Room database, works offline.

## Using the app

- **Users:** no sign-in. Tap your name on the start screen (*Add user* / *Manage users* edit the list).
- **Items, check in / out, activity log, low stock alerts, Excel report:** as before. Regular users can view items and check stock in and out. **Only the Admin can add, edit or remove items.**
- **Hidden Admin entrance:** press and hold the **“DentTrack”** title for **3 seconds** (on the start screen or at the top of the main screen). The first time, you create the Admin password; after that you enter it. The Admin session is never remembered: after a restart, log-out or timeout the password is asked again. There is no password recovery; clearing the app’s data resets it (and deletes all stock data), so keep a backup.
- **Admin menus** (extra tabs, visible only in Admin mode):
  - **Settings:** see below.
  - **Backup:** automatic schedule (Off / Daily / Weekly / Monthly), destinations (Local file storage and/or Google Drive, can be both), *Back up now*, *Import stock from CSV* and *Restore from local file*.
- **Backup file = report file.** A backup is exactly the Excel file from *Reports → Export to Excel* (sheets: Summary, Stock, Low stock, Activity log, Users, Categories). Restoring reads that same file, so a report exported by hand can be restored too. Restore replaces all items and the activity log; users in the file are added, existing users stay; settings and the Admin password are not in the file.
- **Hide Admin user (Settings):** switch on = the Admin is only reachable with the 3-second hold; switch off = an “Admin” card appears on the user selection screen (tapping it still asks for the password).
- **Monthly Report (Reports tab, Admin only):** an Excel file for a chosen year with one sheet per item category (plus an Overview sheet). Every item has three rows, Check in / Check out / Left (stock at the end of the month), with 12 month columns and a Total column. Initial stock (an item added or imported with a quantity) counts as the starting balance, not as a check in. Export it on request, or switch on *Save to Google Drive on the 1st of every month* (folder “DentTrack Monthly Reports”; needs Google Drive connected on the Backup tab; if the tablet is off on the 1st, it is saved the next day it is on and online). On Jan 1 it reports the year that just ended.
- **Settings (Admin)** has two groups: *Admin & user settings* (hide Admin user, Admin password, inactivity timeout) and *Stock management* (low stock threshold).
- **Categories (Items tab, Admin):** the **Add / Edit item** window picks the category from a dropdown. The **Categories** button next to *Add* opens Category management: add or delete categories. Deleting a category keeps its items but blanks their category. Categories created by a CSV import or a restore are added automatically.
- **Import stock from CSV (Backup tab, Admin):** column A = item name, column B = category, column C = quantity; a heading row is allowed; comma, semicolon or tab separated; UTF-8 or Windows-1257. Rows with a negative or empty quantity are skipped (the preview can import negatives as 0). Items that already exist (same category and name) are never changed, so the import can be repeated safely. `stock_import.csv` (made from Current_stock.xlsx) is an example.
- **Activity limits:** the app lists the latest **100** activity entries; the backup/report file keeps the latest **999**. After restoring a backup older history is therefore not available to the Monthly Report.
- **Start screen:** marble-style animated background (soft green from the upper-right corner fading to white, a shine sweeping across every few seconds) and the miclinic logo.
- **Add / Edit item and Check in / out windows** have Cancel and Save in a bar at the top, so the on-screen keyboard cannot cover them.
- **Logo:** `res/drawable-nodpi/logo_miclinic.png` (1122 x 429, transparent, colours matched to the app). To change it, replace that file (same name, no code change).

## Build & run

1. Android Studio Koala (2024.1) or newer. *File → Open…* this folder, let Gradle sync (Android Gradle Plugin 8.7.3).
2. Run on a tablet emulator (e.g. Pixel Tablet) or a real tablet. Android 7.0+ (API 24).

## Backup destinations

- **Local file storage:** by default the app’s own folder (`Android/data/com.stocktrack.app/files/backups`). *Choose folder* lets the Admin pick any folder (for example on an SD card or USB drive); the app keeps access to it.
- **Google Drive (one-time setup by the developer):**
  1. Google Cloud Console → create a project → enable **Google Drive API**.
  2. Configure the OAuth consent screen (add the clinic’s Google accounts as test users while in *Testing*).
  3. Credentials → *Create credentials → OAuth client ID → Android*. Package name `com.stocktrack.app` and the SHA-1 of the key the app is signed with (`./gradlew signingReport`; the debug key and the release key each need their own entry).
  4. In the app: *Backup → Google Drive → Connect*, pick the Google account and allow access. Files are uploaded to a folder “DentTrack Backups” in that Drive. The app only gets the narrow `drive.file` permission (it can see only files it created).

  Needs Google Play services and internet. Scheduled Drive backups wait for a connection. If Google asks for permission again, the Backup screen shows the status of the last run.

## Project layout

```
app/src/main/java/com/stocktrack/app/
  MainActivity.kt          start-up, Admin prompt host
  StockViewModel.kt        stock logic, admin session, settings, backup/restore
  data/Models.kt           User, Item, LogEntry, alert-level rules
  data/Database.kt         Room DAO + database
  data/Settings.kt         saved settings + Admin password (salted hash)
  data/ReportBuilder.kt    the Excel report / backup layout
  data/XlsxWriter.kt       writes .xlsx      data/XlsxReader.kt   reads .xlsx
  data/BackupCodec.kt      restore from a report/backup file
  data/BackupRunner.kt     backup run, WorkManager schedule
  data/MonthlyReport.kt    Monthly Report builder + monthly Drive upload
  data/Csv.kt              CSV reader
  data/StockImport.kt      import items from a CSV stock list
  data/DriveBackup.kt      Google Drive upload
  ui/MarbleBackground.kt  animated start-screen background
  ui/                      screens: UserSelect, Main, Dashboard, Items, Activity, Reports,
                           Settings, Backup, Admin sign-in dialog, shared theme/components
```

The Kotlin package is still `com.stocktrack.app` (renaming a package is an Android Studio refactor and would reset existing app data); everything visible to people says DentTrack.
