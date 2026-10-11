# Changelog

Newest version first. A version's section becomes the release notes on GitHub.

## 1.4.0 — 2026-10-11

- A new look after keril.art: a blue sky with dots, bubbles, stars and hearts, Windows XP style windows
  (the "_" button rolls a window up), glossy buttons, the Monocraft pixel font and an address bar with
  a light/dark switch and a language menu. The theme can also be set in Settings: phone, light or dark.
- Tenshi-chan is now an ice fairy with a big blue bow, ice-crystal wings and a ":3" smile. New app icon,
  themed icon and notification icon.
- The chart opens full screen with a tap: pick 3, 6 or 12 hours and move a finger along it to read every
  value and its time.
- Updates from GitHub: SugarBridge looks for a new version on start (at most every 6 hours) and in
  Settings, downloads it, checks it is SugarBridge signed with the same key and hands it to Android to
  install. Settings also link to the GitHub page.
- The language screen on the first start applies the choice only on "Continue" and already shows its
  texts in the marked language.
- Fixed: the minute-mode notification lagged behind the app when the app itself synced (on opening,
  "Sync now", the background job). It now follows every sync at once.
- "Pin the notification": if it is swiped away by accident, it comes right back.
- Late data: tests showed minute mode delivers every value within a minute, also in deep Doze, while the
  background sync can be held back for hours on an idle phone without the battery exemption. The home
  screen now asks for it whenever sync is on, and shows how long after the sensor reading values arrive.

## 1.3.0 — 2026-10-11

- 12 languages: English, Russian, Ukrainian, German, French, Spanish, Portuguese, Italian, Turkish,
  Simplified Chinese, Japanese and Korean. The language is chosen on the first start and can be changed in
  Settings or in Android's per-app language settings; the notification switches along.
- A new mascot: Tenshi-chan is now a chibi angel girl with sky-blue hair, a halo and wings. She blinks, flaps
  her wings, worries when glucose is low, gets flustered when it is high and sleeps while there is no data.
  New app icon, themed (monochrome) icon and notification icon.
- Errors are stored as codes and shown in the current language, including in the notification.
- LibreView exports in German, French, Spanish, Portuguese, Italian, Turkish and other languages are
  recognized by their column headers, even when the columns are shifted.
- Times follow the phone's 12/24-hour setting, dates and numbers follow the app language.
- English README with a Russian version, English comments and commit messages.

## 1.2.0 — 2026-10-10

- Estimated HbA1c (GMI) for 14 and 90 days on the home screen, from all SugarBridge readings in
  Health Connect, with the share of data: the estimate can be trusted with at least 70% of the data.
- GMI for the chosen period in the LibreView history.
- HbA1c can't be written to Samsung Health: Health Connect has no such data type, and writing to Samsung
  Health directly is only open to Samsung partners. A lab HbA1c can be entered manually.

## 1.1.0 — 2026-10-10

- The full history from LibreView: the built-in site window catches the downloaded CSV by itself; a file can
  also be chosen or opened with SugarBridge. Choose the period and days, write to Health Connect without
  duplicates, delete.
- A new tenshi kawaii interface: the angel Tenshi-chan, a night theme, a 12-hour chart, an app icon.
- Settings: background sync every 15/30/60/180 minutes or off, minute mode with a choice of interval,
  mmol/L or mg/dL, the target range.
- Minute mode: glucose as a number in the status bar, a quiet notification, a wake lock, autostart after
  a reboot and an update.
- If Health Connect is unavailable or outdated, the app shows how to fix it.
- Accounts from any country: the Russian LibreView server and the world one (with regions) are picked
  automatically, and the history window opens libreview.ru or libreview.com by the account's region.
- In Health Connect the sensor is labeled "FreeStyle Libre", which fits Libre 1, 2 and 3.
- Fixed: a reading "from the future" (the sensor clock running ahead) broke the whole write to Health Connect.
- Fixed: when Abbott raises the minimum LibreLinkUp version, SugarBridge adapts instead of signing out.

## 1.0.0 — 2026-10-08

- The first version: LibreLinkUp sign-in, the password under the Android Keystore,
  sync to Health Connect every 15 minutes, a minute service with a notification.
