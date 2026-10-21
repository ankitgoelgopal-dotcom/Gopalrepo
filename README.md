# My Language Keyboard (Android system keyboard / IME)

Works in every app (WhatsApp, Chrome, Notes ...) once enabled.

## Install
Every push to `main` builds an APK and publishes it under **Releases**. Download, install, open the app,
tap "1. Enable keyboard" then "2. Choose keyboard".

## Settings (open the app, everything applies instantly)
- **Keys & layout:** number row on/off, choose which layouts are active (English, AZERTY, QWERTZ, Hindi, Russian,
  Spanish, Greek, Arabic, Custom), and a **custom layout editor** to add / remove / rearrange keys.
- **Suggestions:** on/off, plus your own words (`word = meaning`) on top of the built-in dictionary.
- **Look:** theme, accent colour, key height, key shape.
- **Behavior:** vibration strength, long-press popups, auto-capitalisation.
- **App:** keyboard name (space bar + app header) and launcher icon colour.

## Files
- Built-in dictionary: `app/src/main/assets/dictionary.tsv` (word<TAB>meaning)
- Name shown in Android's keyboard list: `app/src/main/res/values/strings.xml`
