# My Language Keyboard (Android system keyboard / IME)

Works in every app (WhatsApp, Chrome, Notes ...) once enabled, like a hieroglyph or futhark keyboard.

## Build the APK
1. Install Android Studio (free).
2. File > Open > select this folder (NewLanguageKeyboard). Let Gradle sync.
3. Plug in your phone (USB debugging on) and press Run, or Build > Build APK(s).
4. Install the APK, open the app, tap "1. Enable keyboard" then "2. Choose keyboard".

## Features
- Full QWERTY + number row (all your words end in numbers) + symbols page
- Suggestion bar: type "lo" -> loka1 · food ; or type English "food" -> loka1
- Vi key inserts the plural marker
- Family words, plural system and particles included
- Dictionary: app/src/main/assets/dictionary.tsv (word<TAB>meaning). Edit/add lines freely.
- Rename the keyboard in app/src/main/res/values/strings.xml
