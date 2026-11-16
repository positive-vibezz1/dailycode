# Daily Code: a daily C and Python challenge widget

A home-screen widget for your Pixel that shows a new coding challenge every day.
Tap it for the full question, a hint, the expected output and a worked solution.

- **60 challenges** (30 C + 30 Python), ordered from easy to hard
- **Mix mode** alternates Python and C each day, or pick just one language
- **Streaks:** tap *Mark solved* on the widget or in the app to build your 🔥 streak
- **Material You:** the widget follows your wallpaper colours and dark mode
- Works offline: no account, no internet, no ads, no permissions

---

## 1. Install it on your Pixel

Android only installs widgets that come with an app, so you need to build this
project into an app (an `.apk` file) once. Pick whichever option suits you.

### Option A: Android Studio (on a Windows, Mac or Linux computer)

1. Install **Android Studio** (free): https://developer.android.com/studio
2. Unzip `DailyCode.zip`. In Android Studio choose **File → Open** and select
   the `DailyCode` folder. Wait for the "Gradle sync" to finish. The first time
   it downloads a few hundred MB. If it offers to upgrade the Android Gradle
   Plugin, accepting is fine.
3. On your Pixel, turn on developer mode: **Settings → About phone →** tap
   **Build number** 7 times. Then go to **Settings → System → Developer options**
   and turn on **USB debugging**.
4. Plug the phone in with a USB cable and allow debugging when asked. Pick your
   Pixel in the device menu at the top of Android Studio and press the green ▶ **Run** button.

Prefer a file? Use **Build → Build App Bundle(s) / APK(s) → Build APK(s)**,
copy `app/build/outputs/apk/debug/app-debug.apk` to your phone, and open it.

### Option B: GitHub (no Android Studio needed)

1. Create a new repository on github.com and upload everything in the
   `DailyCode` folder. Make sure the `.github/workflows/build.yml` file is included.
   It's in a hidden folder. If your upload skipped it, go to the **Actions** tab,
   choose **set up a workflow yourself**, and paste in that file's contents.
2. GitHub builds the app automatically (about 3 minutes). Watch it in the
   **Actions** tab.
3. On your Pixel, open `https://github.com/<you>/<repo>/releases/latest` and
   tap **DailyCode.apk** to download and install it.

When you install an APK this way, Android will ask you to allow installs from
your browser or file manager. If Play Protect warns about an unknown app,
choose to install anyway. It's your own build.

## 2. Add the widget

Long-press an empty spot on your home screen → **Widgets** → **Daily Code** →
drag it into place. The default size is 4×2. Stretch it taller (4×3 or more)
to see the whole question on your home screen.

## How it works

| Where | What it does |
|---|---|
| Widget | Shows today's language, difficulty, day number, streak, title and question |
| Widget → 💡 Hint | Opens the app with the hint already showing |
| Widget → Mark solved | Marks today as done (tap again to undo) |
| App | Full question, expected output, hint, solution, copy button, bonus questions |
| App → Language | **Mix** (alternating), **C** only, or **Python** only |

- **Day 1** is the day you first open the app or add the widget. The next
  question appears just after midnight.
- In Mix mode, the 60 questions last two months, then start again.
- **Copy question** puts the challenge on your clipboard, ready to paste as a
  comment into whatever code editor you use.

## Add your own questions

All questions live in `app/src/main/assets/questions.json`. Each entry looks like this:

```json
{
  "id": "py31",
  "lang": "Python",
  "difficulty": "Medium",
  "title": "Your title",
  "prompt": "What to build.",
  "output": "The exact expected output",
  "hint": "A nudge in the right direction.",
  "solution": "print('the code')"
}
```

`lang` must be `"C"` or `"Python"`. Questions appear in file order, so put
harder ones further down. Rebuild the app after editing.

## Project layout

```
app/src/main/
  java/com/dailycode/app/
    DailyCodeWidget.java   the home-screen widget and midnight refresh
    MainActivity.java      the full question screen
    DailyPicker.java       which question is today's, solved days, streaks
    QuestionBank.java      loads questions.json
    Question.java          one question
  res/layout/              widget layouts (compact and tall) and the app screen
  assets/questions.json    the challenges
```

Built with plain Java and the Android SDK only, with no extra libraries.
Requires Android 12 or newer. Built against Android 16 (API 36), and runs on newer versions too.
