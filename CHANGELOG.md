# Changelog

Newest version first. A version's section becomes the release notes on GitHub.

## 1.8.0 — 2026-10-12

- The glucose in the status bar is larger and its sizes are independent. The number is measured by its digits:
  a decimal comma hangs below them instead of making them about a fifth smaller. The arrow's size never changes
  the number any more: when the square runs short, the digits get narrower and then the arrow gives way. Beside
  the number (now the default) the number is as tall as the clock's digits. The sizes are multipliers, two
  smaller and two larger: ×0.8, ×0.9, ×1.1, ×1.25 instead of S, M, L, XL.
- "Arrow position" shows both choices as tiles in the app's style, each drawn on a status bar with the real
  icon; the preview's status bar follows the light or dark theme.
- Turning minute mode on switches background sync off: it would only fetch the same, less often. A pink notice
  at the bottom of the screen says so and stays until its link to the setting or its ✕ is tapped. When minute
  mode is turned off, background sync comes back with its interval.
- Progress bars behave as in Windows XP: whole blocks appear one after another. Reading a file shows a group of
  blocks running through the bar.
- "About SugarBridge": the GitHub button has a chain to show it is a link. A new "About the author" window below
  it has the website keril.art and the contacts from it: email, Discord and Telegram.
- Fixed: "Clear" in the developer tools' log left Samsung Health's lines, which are read from Android's usage
  statistics each time. Lines older than the moment of clearing aren't shown again.

## 1.7.1 — 2026-10-12

- Fixed: with the notification pinned, every new value moved SugarBridge's icon in the status bar ahead of other
  ongoing notifications (a VPN's, say) and a moment later back again. The notification now keeps one time while
  minute mode runs, so Android keeps it in its place and only the number and the arrow change.
- Russian and Ukrainian say «Эл. почта» / «Ел. пошта» instead of "Email". All texts in the 12 languages checked
  again for untranslated words, quotes, spaces and names.
- The bug report form on GitHub asks for the developer tools' event log, which shows the cause fastest. The
  READMEs answer whether SugarBridge is on Google Play or the iPhone.
- Tests: the notification keeps its time across updates while its content changes. The device tests open the app
  the way the launcher does: their own start counted as one from the background, which Android silently drops
  some time after the app was last on screen, so a test that ran after a long one sometimes never started.

## 1.7.0 — 2026-10-12

- The glucose in the status bar is much larger: the number fills the icon and the trend arrow stands above it
  with a clear gap, instead of a small number squeezed next to a small arrow. A new "Status bar" window in
  Settings turns the number and the arrow on or off, sets the size of each (S, M, L, XL) and puts the arrow
  above the number or beside it, with a preview drawn by the same code as the real icon.
- History always at hand: the History tab has a start page, and the loaded history opens from it with "View the
  loaded history", also after a restart; "Back" returns to the start page. "All-time history" under the home
  chart leads straight there.
- Updating the history: a newer export is compared with the loaded one (new, the same, different, only in the
  loaded history), and you choose to add the new readings, add them and replace the differing ones, or replace
  the whole history. "Delete the loaded history" removes it from SugarBridge after a confirmation; Health Connect
  keeps what was written. The history is kept in a file of its own; the raw file kept by 1.6 is converted.
- Messages pop up in pink windows that stand out from the screen. "Check connection" shows its answer right
  under the button.
- Developer tools: every red line has a button that fixes it (minute mode, battery limits, battery saver, the
  Health Connect permission, installing Samsung Health), and there is a way to give usage access when Android
  greys it out for an app installed from an APK, with a button to the app's page. Copying the log no longer
  shows a message of its own: Android confirms it.
- Corrected: the developer tools and Settings said that opening Samsung Health makes it take new values at once.
  Samsung only says it syncs when its home screen is opened or pulled down; the texts now say so.
- Fixed: a kept history that could no longer be read showed an error on every start; it is dropped quietly. An
  import still being read when another file came could bring the older history back.
- Tests: the status bar icon is measured by its pixels; the Status bar window; the history kept across restarts
  (also from 1.6's file), merged in each of the three ways and deleted; the developer log recording syncs and the
  screen going off and on; the latency test checks every value that was due and unlocks the screen first.
  Tidier code: the status bar icon, the Status bar window and the history chart have files of their own.

## 1.6.0 — 2026-10-11

- History chart: after importing a LibreView export, look through it as a chart, a day, a week, a month, three
  months, all of it or any period from the calendar, with arrows to step through time, the statistics and the
  estimated HbA1c of the period. A tap opens it full screen with dates under the finger. The imported file now
  stays in the app until "Close", so it is still there after a restart.
- A new calendar for periods: ready-made periods, "From" and "To", a month with a dot on every day with data
  (colored by time in range), and a grid of months to get years back in three taps.
- Text size in Settings, and a low vision mode: the phone's own font, larger text, more contrast, a plain
  background.
- The number in the status bar and the trend arrow each have a switch. With the arrow on, the status bar shows it
  next to the number too.
- Developer tools (Settings → About SugarBridge): the state of everything that can delay glucose (minute mode,
  background job, battery limits, Android's standby bucket, Doze, Health Connect, Samsung Health) and an event
  log of every sync with its trigger, timings and the age of the newest value, the minute service, Doze and the
  screen. With usage access it also shows when Samsung Health was opened or ran a background service. The log
  can be copied or shared.
- The glucose value sits in the middle between Tenshi-chan and the window edge.
- Checked: the trend arrow is LibreLink's own (LibreLinkUp's TrendArrow 1–5: ↓ ↘ → ↗ ↑), now pinned by a test.
- Fixed: with a large phone font the LibreView site broke its layout: words spilled out of its cards and the
  header was cut. The site now keeps its own text size; pinch to zoom instead.

## 1.5.2 — 2026-10-11

- Tenshi-chan’s ahoge now rises through her halo: the near side of the ring passes in front of it. The app
  icon too.
- Tighter lines everywhere, and a little more room between a window’s title and its content.
- Messages (e.g. after “Sync now”) pop up as a small window like all the others, without the white frame;
  a tap closes one.
- The background is tinted a little more in both themes, so text on it reads better.
- Clearer sync settings: the minute mode comes first, with one switch and a plain explanation; background
  sync says right away that it is separate from the minute mode and not needed for it.
- “Display” is now “Readings”, and the target range explains what it is for: colors, the chart band and the
  “in range” share.
- Fixed: “Permissions” in Settings did nothing once writing was allowed. Now “Allow writing” shows only while
  it is missing; if Health Connect has stopped asking after two refusals, SugarBridge opens it and says
  where to allow it.

## 1.5.1 — 2026-10-11

- Fixed: the LibreView site did not open properly. The page was laid out with zero height, so its sign-in
  sat squeezed in a tiny box. Now the site gets the whole screen below one compact row with "?" (how to
  download the history) and "Close", and it makes room for the keyboard.

## 1.5.0 — 2026-10-11

- Tenshi-chan is the angel again: sky-blue hair, a halo and feathered wings, now with a red ribbon. Tap her and
  she tilts her head, closes her eyes and smiles. The app icon is back too.
- The target range is typed in: two fields in the chosen unit and a "Save" button that only works for a range
  that makes sense. The saved range is shown on its own line.
- Calmer screens: no more address bar at the top (the theme and the language are in Settings), no window
  buttons, and the background no longer moves: no bubbles, stars or hearts, only the still field of dots.
- The "Now" tab has a house icon.
- Fixed: changing the language made the whole screen flash black. The screen now stays and its texts switch
  to the new language in place.

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
