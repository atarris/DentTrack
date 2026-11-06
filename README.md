# DentTrack – dental storage tracking (Android tablet)

A BoxHero-style stock app for dental clinics. Kotlin, Jetpack Compose (Material 3), local Room database, works offline.

## Features for each user

### Everyone (regular users)

No password: tap your name on the start screen to open your menu.

| Menu | What you can do |
|---|---|
| **Dashboard** | See the number of items, quantity in stock, low stock alerts and stock value. Tap a low stock alert to open the item. Tap a Recent activity entry to open that item for check out. |
| **Items** | Search items and filter by low stock or out of stock. See an item's details and history. **Check in** and **Check out** stock, with a note. |
| **Activity** | Read the activity log (latest 100 entries). Search it and filter by check in, check out or item changes. |
| **Reports** | See stock by category and which items need restocking. Export the Storage report to Excel. |
| **Info** | See the app version, build number, build date and who built it. Choose the app's color scheme. |

The box icon at the top left, or *Switch user*, goes back to the start screen.

### Admin (everything above, plus)

Open with a press and hold of 3 seconds on the **DentTrack** title, then enter the Admin password.

| Menu | What the Admin can do |
|---|---|
| **Items** | Add, edit and remove items. Add and delete categories. |
| **Activity** | Clear the log. |
| **Reports** | Export the Monthly Report instead of the Storage report (charts, Top 10 items, month and year review, a sheet per category), or save it to Google Drive, on request or on the 1st of each month. |
| **Users** | Add and remove users. |
| **Settings** | Language (English or Lietuvių). GUI color scheme. Show or hide the Admin on the start screen. Change, remove or set the Admin password. Inactivity timeout. Low stock threshold. Web server. Reset app configuration. Clear stock database. |
| **Backup** | Automatic backup schedule. Backup to a local folder and/or Google Drive. Back up now. Import stock from CSV. Restore from a backup file. |
| **Web page** | Manage stock from a browser on the same network: see *Web server* below. |

## Using the app

- **Language (Settings, Admin):** the screens can be English or Lithuanian, whatever language the tablet uses. In Lithuanian, Check in and Check out are *Pridėti* and *Paimti*, the report titles are *Sandėlio ataskaita* and *Mėnesinė ataskaita*, and the default unit `pcs` shows as *vnt*. Excel reports, backup files and their names always stay in English (the unit is saved as `pcs`), so backups restore in either language. Entries already in the activity log keep the wording they were saved with. Texts are in `res/values/strings.xml` (English) and `res/values-lt/strings.xml` (Lithuanian).
- **Info:** shows the version, build number, build date and author. The build date is set automatically when the app is built.
- **GUI color scheme (Info, everyone; Settings, Admin):** *Mint* (the original), *Blue* (the clean look of the web page: plain background, no shadows), *Amber*, *Violet*, *Pink* or *Lagoon* (teal with warm coral highlights), or *Random every day*, which shows a different one each day. Any user can change it, also the Admin on the web page, which shows the color in use. It changes the app for everyone at once; the web page keeps its own look.
- **Hidden themes:** on by default. The start screen changes by itself, with animations and a Lithuanian greeting: Valentine's Day Feb 8-14, Vasario 16-oji Feb 15-21, Kovo 11-oji Mar 5-11, Joninės Jun 18-24, Mindauginės Jun 30-Jul 6, Halloween Oct 24-30, Beata's birthday Oct 31-Nov 2, Christmas Dec 19-25, New Year Dec 26-Jan 1. The Admin opens the switch and previews with a 3-second hold on the *GUI color scheme* title in Settings; leaving the tab hides them again. The dates are in `data/Seasons.kt` and the pictures in `ui/SeasonScenes.kt`.
- **Web server (Settings, Admin):** switch it on in *Settings → Web access*, and the card shows the address to open, such as `http://192.168.1.20:8080`. The page works from any computer or phone on the same network and lets the Admin add, edit and remove items, check stock in and out, manage categories and users, read the activity log, download the Storage and Monthly reports, and change the safe settings (Settings tab: language, color scheme, hide Admin, inactivity timeout, low stock threshold). Changes appear on the tablet straight away and are logged under Admin. The language follows the app.
  - **Sign-in:** only the Admin, with the Admin password. While that password is off, the page asks to turn it on in the app and nobody can sign in. Changing or removing the password signs everyone out. Five wrong passwords lock the sign-in for 5 minutes, and a session ends after 30 idle minutes.
  - **Keep in mind:** the connection is plain HTTP and not encrypted, so use it only on a trusted network, not guest Wi-Fi. Only addresses on the local network are answered. The tablet must stay on and connected, so keep it plugged in. The server keeps running with the app closed (with a permanent notification) and comes back after a restart. Give the tablet a fixed address in the router so that the page address stays the same. Reset app configuration switches the server off, and then it must be switched on again on the tablet. Changing the Admin password, switching the web server off, reset, clearing the stock database or the log, hidden themes, the monthly Drive upload, all backup settings, restoring a backup and connecting Google Drive stay on the tablet only, and the server has no calls for them.
- **Users:** no sign-in. Tap your name on the start screen to open your menu. Tapping the box icon at the top left of the main screen goes back to the start screen. The Admin adds and removes users on the **Users** tab.
- **Dashboard → Recent activity:** tapping an entry opens that item with the Check out window. An item with no stock opens without the window, and entries of removed items do nothing.
- **Items, check in / out, activity log, low stock alerts, Excel report:** as before. Regular users can view items and check stock in and out. **Only the Admin can add, edit or remove items.**
- **Hidden Admin entrance:** press and hold the **“DentTrack”** title for **3 seconds** (on the start screen or at the top of the main screen). The first time, you create the Admin password; after that you enter it. The Admin session is never remembered: after a restart, log-out or timeout the password is asked again. There is no password recovery; clearing the app’s data resets it (and deletes all stock data), so keep a backup. If the password was removed in Settings, the hold opens the Admin menu directly.
- **Admin menus** (extra tabs, visible only in Admin mode):
  - **Users:** add and remove users.
  - **Settings:** see below.
- **Clear log (Activity tab, Admin):** deletes all activity entries after a confirmation. Stock quantities do not change, but Monthly Reports lose the movements that were in the log.
  - **Backup:** automatic schedule (Off / Daily / Weekly / Monthly), destinations (Local file storage and/or Google Drive, can be both), *Back up now*, *Import stock from CSV* and *Restore from local file*.
- **Backup file = report file.** A backup is exactly the Excel file from *Reports → Export to Excel* (that button is shown to regular users; the Admin uses the Backup tab), saved under the same name, `DentTrack_Backup_yyyy-MM-dd_HHmm.xlsx` (sheets: Summary, Stock, Low stock, Activity log, Users, Categories). Restoring reads that same file, so a report exported by hand can be restored too. Restore replaces all items and the activity log; users in the file are added, existing users stay; settings and the Admin password are not in the file.
- **Hide Admin user (Settings):** switch on = the Admin is only reachable with the 3-second hold; switch off = an “Admin” card appears on the user selection screen (tapping it still asks for the password).
- **Monthly Report (Reports tab, Admin only):** an Excel file for a chosen year with one sheet per item category (plus an Overview sheet). Every item has three rows, Check in / Check out / Left (stock at the end of the month), with 12 month columns and a Total column. Initial stock (an item added or imported with a quantity) counts as the starting balance, not as a check in. In front of those are two review sheets with charts, counted in units checked out: the month (a pie chart by category and the Top 10 items) and *Year yyyy* (categories by month with a stacked chart, the year's share by category and the Top 10 items). Each item's three rows have a line above them and every second item has a light background. Export it on request, or switch on *Save to Google Drive on the 1st of every month* (folder “DentTrack Monthly Reports”; needs Google Drive connected on the Backup tab; if the tablet is off on the 1st, it is saved the next day it is on and online). On Jan 1 it reports the year that just ended.
- **Settings (Admin)** has four groups: *Admin & user settings* (hide Admin user, Admin password, inactivity timeout), *Stock management* (low stock threshold), *Web access* (web server) and, at the bottom, *Reset and clear* (reset app configuration, clear stock database).
- **Clear stock database (Settings):** after typing DELETE, removes all items and categories, optionally also the activity log. Users, settings and the Admin password stay. Only a backup can bring the stock back.
- **Admin password (Settings):** *Change password* or *Remove password*; both need the current password. Once removed, the card offers *Set password*.
- **Reset app configuration (Settings):** puts the settings and backup options back to their defaults (automatic backups and the monthly Drive upload off, backup folder and Drive connection forgotten). Stock, the log, users, categories and the Admin password stay.
- **Categories (Items tab, Admin):** the **Add / Edit item** window picks the category from a dropdown. The **Categories** button next to *Add* opens Category management: add or delete categories. Deleting a category keeps its items but blanks their category. Categories created by a CSV import or a restore are added automatically.
- **Import stock from CSV (Backup tab, Admin):** column A = item name, column B = category, column C = quantity; a heading row is allowed; comma, semicolon or tab separated; UTF-8 or Windows-1257. Rows with a negative or empty quantity are skipped (the preview can import negatives as 0). Items that already exist (same category and name) are never changed, so the import can be repeated safely. `stock_import.csv` (made from Current_stock.xlsx) is an example.
- **Activity limits:** the app lists the latest **100** activity entries; the backup/report file keeps the latest **999**. After restoring a backup older history is therefore not available to the Monthly Report.
- **Start screen:** animated background (soft mint waves in the upper-left and lower-right corners, marble veins, a shine sweeping across every few seconds), the Mi’ clinic logo and a grid of user cards. Up to two rows of cards fit without scrolling (the logo and avatars shrink if the screen is short); three or more rows scroll. Colours are the `DentTrackColors` tokens in `ui/Common.kt`.
- **Add / Edit item and Check in / out windows** have Cancel and Save in a bar at the top, so the on-screen keyboard cannot cover them.
- **Logo:** `res/drawable-nodpi/logo_miclinic.png` (1200 x 165, transparent). To change it, replace that file with one of the same proportions (same name, no code change).

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
  data/Language.kt         the language chosen in Settings
  data/StockActions.kt     stock changes shared by the screens and the web page
  web/HttpServer.kt        small HTTP server        web/WebApi.kt   web page sign-in and calls
  web/AppWebBackend.kt     web page link to the data   web/WebServerService.kt   keeps it running
  ui/MarbleBackground.kt  animated start-screen background
  ui/                      screens: UserSelect, Main, Dashboard, Items, Activity, Reports,
                           Users, Settings, Backup, Info, Admin sign-in dialog, shared theme/components
```

The web page itself is in `app/src/main/assets/web/`.

The Kotlin package is still `com.stocktrack.app` (renaming a package is an Android Studio refactor and would reset existing app data); everything visible to people says DentTrack.
