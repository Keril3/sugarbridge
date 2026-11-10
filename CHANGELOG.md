# Changelog

Newest version first. A version's section becomes the release notes on GitHub.

## 1.15.0 — 2026-11-10

Faster, and much lighter on the battery. Measured on an Android 14 emulator against 1.14.1.

Battery:

- Minute mode no longer keeps the processor awake around the clock. Without battery restrictions an exact alarm wakes
  the phone for each poll and it sleeps in between: over 10 minutes with the screen off the processor was held for
  0.2 s instead of 9 min 59 s, with the same 10 polls, and SugarBridge used 0.15 s of processor time instead of 23.5 s.
  With battery restrictions Android allows no such alarm, and "Keep the phone awake" works as before.
- One encrypted connection now serves poll after poll, instead of a new one every minute with its extra radio time.
- With no connection, after three failures in a row, or while the newest reading is half an hour old, minute mode asks
  every 5 minutes at most. When the connection comes back it asks at once.
- The estimated HbA1c is kept between starts and counted again every 6 hours at most: with minute mode it means reading
  over a hundred thousand records. It also shows at once when the app opens.
- Health Connect is asked which days of a kept history it has only when the History tab opens, not on every start.
- Tenshi-chan stands still in battery saver.

Speed:

- The release is now optimized with R8 and comes with a startup profile: the app went from 31 MB to 6 MB, a cold start
  from 648 to 457 ms, and slow frames while scrolling the home screen from 12.4% to 1.1%.
- Less happens on the thread that draws the screen when the app starts and every time you return to it.
- A row of the history's days costs one drawing as it scrolls into view.

Also: the estimated HbA1c appears after the very first sync instead of after the next start. Because instrumented tests
can't run against optimized code, `tools/smoke_test.py` walks through an optimized build before a release.

## 1.14.1 — 2026-11-09

An audit of the whole app for security problems, bugs and slowness.

Security:

- Another app on the phone could make SugarBridge launch any screen in its name, by passing an Intent off as the
  package installer's answer. Through the LibreView window that could reach the LibreView sign-in. The installer's
  answer and the update notification's buttons now come through an entry only SugarBridge can open.
- The LibreView window: files are taken only from LibreView's own pages, the sign-in cookies go only to LibreView,
  only https is followed, pages can't read files on the phone, and a page of another site is named in the title.
  A script in a frame of another site can no longer hand over a file. Signing out of SugarBridge also signs out of
  this window.
- A file opened from another app is read only through a `content:` address and up to 32 MB; a `file:` address could
  point at SugarBridge's own files.
- The region LibreLinkUp names is checked before it becomes part of the address the password goes to. Errors no
  longer copy a server's whole answer into the event log, and printing an account shows no password or token.
- A sign-in that was still running while you signed out could save the password again. It no longer can.
- Updates: the file name no longer comes from the release, a download is limited in size and checked for being
  complete, and it is removed from the cache after installing.
- Juggluco's and xDrip+'s broadcasts are taken at most once every 10 seconds, so a flood from another app can't wear
  out the storage or the battery. A notification that can't be read can't bring the app down.
- Guava, brought in by Health Connect, updated from 31.1 to 33.8: the old one has published vulnerabilities. CI's
  token is read-only, and the build's actions are pinned to exact commits.
- The event log and the LibreView sign-in don't move to a new phone. A copied log is marked as sensitive.

Bugs:

- One cut-off line in the kept history, from the phone switching off while a sync added readings, deleted the whole
  history. Now only that line is lost, and adding goes on after it. A read that fails for a moment no longer deletes it.
- A CSV with an impossible date (year 1 or 9999) crashed the app or hung the calendar. Such rows are now skipped.
- One bad point from LibreLinkUp made the whole sync fail, or Health Connect refuse the whole batch, for up to 12 hours.
  Now only that point is left out.
- A broken `data:` link in the LibreView window crashed the app; so did the window's page process dying, which took
  minute mode down with it.
- A notification with just a whole number, such as "60", could be read as a glucose of 60 mg/dL when the app is in
  mmol/L. A number without a unit now has to look like the unit in use.
- Swiping away the notification minute mode left behind no longer starts an idle service.
- Failed updates and LibreView downloads now read as sentences: the technical part goes to the event log.
- "Done" on the keyboard signs in.

Speed and battery:

- Tenshi-chan redraws at 30 frames a second instead of the screen's full rate (up to 120), and not at all when she is
  scrolled off the screen or the phone's animations are off. On the sign-in screen with nothing happening this draws
  half the frames and takes half the processor time (measured on the emulator: 69% → 34% of a core).
- A sync no longer redraws the screen once for every saved value, and does its work away from the main thread.
- Moving a finger along a chart no longer redraws the whole chart at each step.
- Passes over a long history (writing, deleting, a new target range) moved off the main thread; a long CSV is read
  faster and with less memory.
- The notification's icon is drawn only when something changed. The estimated HbA1c is recalculated at most every 30
  minutes. The LibreView page doesn't run in the background.

## 1.14.0 — 2026-10-17

- Failures read as sentences. Without the internet the notification and the home screen said "no connection to
  LibreLinkUp" followed by Android's own words (`Unable to resolve host "api.libreview.io"…`); a failed sign-in
  could show LibreLinkUp's raw answer, and any other failure the exception's text. Now they say "the phone has
  no internet", "LibreLinkUp can't be reached", "LibreLinkUp's server has a problem (code 503)" or "something
  went wrong", in the app's language; the technical detail goes to the event log only.
- Minute mode's notification without the internet or LibreLinkUp: "No internet" or "No connection to
  LibreLinkUp", with the last value and its time and "SugarBridge tries again by itself".
- Settings → Sync → "Without the internet": the glucose can come from apps on the phone and reach Health Connect
  and Samsung Health with no internet at all.
  - "From LibreLink's notification" (experimental): with notification access SugarBridge reads the glucose from
    the notifications of Abbott's apps, if they show it, and skips all other notifications without keeping any.
    What LibreLink's notification holds is shown in Settings and the event log, so it is clear whether it works
    on the phone.
  - "From Juggluco or xDrip+": these apps connect to the sensor themselves and send each reading on the phone
    (Juggluco's "Glucodata broadcast", xDrip+'s local broadcast). The receiver is off until this is turned on,
    and a reading that can't be right is dropped.
  - Each reading is written to Health Connect once a minute whoever brings it, shows on the home screen and in
    the notification, goes into the history if it grows by itself, and to the event log ("On the phone"). While
    such readings come, LibreLinkUp being out of reach is no error on screen.
- Intercepting LibreLink's traffic or reading its data on the phone is not possible: its connection to Abbott
  is encrypted and its data is closed to other apps; the README explains the ways that are.
- The Privacy window says what notification access is used for. The developer tools' status shows the sources
  without the internet and what came last.
- Tests: Juggluco's and xDrip+'s broadcasts, LibreLink's notification with and without a value, the switches,
  notification access, failures as sentences, minute mode without the cloud and with local glucose.

## 1.13.0 — 2026-10-12

- Fonts: Settings → Look → Font. "Like the phone" (the default) is the font chosen in the phone's display
  settings, Samsung's font styles included. "Pixel" is the font of earlier versions; "Noto Sans" and "Noto
  Serif" come with the app. The fonts that come with the app have their own Chinese, Japanese and Korean
  letters, so all twelve languages look alike in them; they are cut to the app's texts, which keeps them at
  4 MB together. Low vision mode shows the phone's font instead of the pixel one.
- Settings in four tabs: Sync (LibreLinkUp, Health Connect, minute mode, background sync), Glucose (unit,
  target range, status bar), Look (theme, font, text, language) and App (about, updates, developer tools,
  feedback). The tabs stay in place and each starts at its top; the background notice's link opens the Sync
  tab. The settings tabs and the tab bar at the bottom shrink a long name instead of wrapping it.
- A neutral theme: soft warm greys without stars and bright colours, muted buttons, easy on the eyes; light
  or dark like the phone. Minute mode's notification follows it like the phone's theme.
- History → "Add new readings to the history": each sync adds its readings to the kept history, one per 15
  minutes like LibreView, so no new CSV is needed. Turned on, it fills the time since the end of the history
  from SugarBridge's records in Health Connect, or begins a history with the last 90 days of them; without a
  history the next sync begins one. An open history shows the new days at once. Deleting the history turns it
  off, so the next sync doesn't begin a new one.
- The developer tools have a window of their own on the App tab with their switch, no longer inside About
  SugarBridge.
- The event log is one window with its entries inside it. A filter shows one kind of event (syncs, minute
  mode, background, phone, errors, Samsung Health); each part of an entry after ";" starts a line of its own.
  The log keeps 1, 3 (the default), 7 or 14 days; older lines are deleted by themselves, and Samsung Health's
  activity is shown for the same days.
- When Samsung Health takes the glucose from Health Connect can't be shown in the log: Android gives the record
  of who read what only to Health Connect's own settings ("Recent access"), not to apps. The README says so.
- tools/make_fonts.py builds the fonts from the Noto and Fusion Pixel sources, and a unit test checks that every
  letter of every language is in them. The OFL licenses of Noto and Fusion Pixel are in licenses/.
- Tests: the log's days, filter and lines, the history growing with syncs (also from Health Connect), plurals in
  every language, the screens of each settings tab, the fonts and the neutral theme.

## 1.12.1 — 2026-10-12

- Health Connect's "privacy policy" link for SugarBridge, on its permission screen and in the permission
  settings, opened just the home screen. It now opens a Privacy window: SugarBridge writes only blood glucose
  and reads back only its own records, the password goes only to LibreLinkUp, the app talks to nothing but
  LibreLinkUp and GitHub, and nothing is collected. Settings → About SugarBridge has a button for it too.
- The app icon is drawn at 86%: the halo and its glow and the wing tips now stay inside the area every
  launcher shows, where round masks used to cut the halo. The themed (monochrome) icon matches.
- Health Connect's screens showing SugarBridge with an old icon: the READMEs explain why (Health Connect keeps
  the icons it loaded and stores the one from the first write) and what helps.
- Tests: the privacy window from both Health Connect links. The device tests that open the app get a GitHub
  without releases, so an update offer can't cover their screens. The latency test opens the app again when
  Android drops the start after a long run of tests, which made it fail now and then.

## 1.12.0 — 2026-10-12

- Settings → About SugarBridge: "Check for updates by itself" (when the app opens, at most every 6 hours) and
  "Offer to install" can be turned off. Without the check by itself SugarBridge asks GitHub only when "Check for
  updates" is tapped.
- A new version is offered in a window that pops up when the app finds it: "Download and install", "What's new"
  (the release page) or "Later". While the app is closed a daily check offers it in a notification that pops up,
  with "Download and install". Each version is offered once in each way; the banner on the home screen and the
  About window keep showing it.
- Fixed: an update that failed on the network said "no connection to LibreLinkUp", and a failure on GitHub's side
  "LibreLinkUp answered with code …". They now name GitHub.
- Tests: the notification once per version and only with both switches on, the window's answer, the daily check
  following the switches, GitHub's errors, the window on screen.

## 1.11.0 — 2026-10-12

- One status bar icon again: the two notifications of 1.10.0 (one for the number, one for the arrow) are gone,
  and the arrow's own notification left by that version is cleared away. "Beside it" draws the arrow next to
  the number in one icon with the font's own proportions; "Above the number", where the digits come out
  larger, is the default again.
- Minute mode's notification follows the app's theme instead of being pink: the phone's own look with "Phone
  theme", a light background with the light theme and a dark one with the dark theme. Changing the theme
  redraws it at once.
- Settings → Status bar: the note under the arrow's place explains why the digits are larger over the number.
- Tests: the icon for every choice of parts, the notification's background in each theme.

## 1.10.0 — 2026-10-12

- The status bar, anew: with "Beside it" the number and the arrow are two icons side by side, two notifications,
  each filling its own square, so both are large; "Above the number" keeps them in one icon. Minute mode's
  notification is now pink, a colourful foreground notification, which Android leaves out of the bundles it
  makes of an app's notifications; if a phone bundles the two icons all the same, SugarBridge notices it within
  seconds and shows the arrow over the number in one icon instead of Tenshi-chan, and Settings say so.
- The Live Update capsule of 1.9.0 is gone: Samsung marked the notification a Live Update but drew no capsule,
  so the status bar showed the arrow alone.
- Without the number the status bar shows the arrow alone, without the arrow the number alone; Tenshi-chan's icon
  comes only without both. Turning the number off no longer greys out the arrow's settings.
- Settings → Status bar: the arrow's place has its two tiles drawn as the real icons; the second preview under
  the sizes is gone. ×1.25 makes the number as large as its icon allows for each value, so every step counts.

## 1.9.0 — 2026-10-12

- Android 16 and newer: minute mode's glucose is a Live Update, a capsule next to the clock. The trend arrow is
  its icon and the number its text, both as large as the clock's digits, which the square notification icon
  can't give. While SugarBridge itself is open, Android hides the capsule; the icon then shows the number.
  Settings → Status bar has a switch "Capsule by the clock" with a preview and, if Android doesn't allow Live
  Updates for the app, a button to its setting. The developer tools show whether Android made the notification
  a Live Update. Phones that don't draw the capsule keep the square icon as before.
- Estimated HbA1c: the bars show the time in the target range over 14 and 90 days, each 15 minutes weighing
  the same, like the days of the history. The share of data is shown only when it is too little to trust the
  estimate. Changing the target range recounts it.
- XP bars: the blocks fill the trough from end to end instead of leaving a strip at the end, and the end blocks
  follow its rounding instead of standing out over the frame.

## 1.8.1 — 2026-10-12

- Status bar: the digits keep the font's own proportions with the arrow beside them too, the same as above
  them; they are no longer squeezed. The number and the arrow are sized in one unit: at the same step the arrow
  is as tall as the digits. While both fit the square, their steps are independent; at the largest steps both
  are made smaller by the same factor and keep the chosen proportion. With the arrow above the number the digits
  are larger, so that is the default again.
- Estimated HbA1c: the share of data is counted by time. Libre's readings come every 15 minutes but a minute or
  two off, so two of them sometimes fell into one 15-minute slot and the next stayed empty: full data showed as
  99% with a bar one block short. A bar now shows the nearest number of blocks, so 100% is a full bar.
- Progress bars show the real progress: the first value at once, new blocks one after another but never more
  than a quarter of a second behind. The LibreView page's bar stays full for a moment when the page has loaded
  instead of vanishing a few blocks short. Work without a known end shows blocks running through the bar: buttons
  while their request runs (sync, sign in, check connection, writing days), fetching and checking the LibreView
  file, the developer tools' first check.
- "About the author" is now "Feedback": how to reach the developer first, then the developer's website.
- History: the "Days" heading with "New", "All" and "None" stands on a glass bar with small buttons; right on the
  sky the blue links were hard to read in both themes.

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
