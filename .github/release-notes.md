A modern IPTV player. It loads the [iptv-org](https://github.com/iptv-org/iptv) public playlist and the [epgshare01](https://epgshare01.online/) TV guide by default.

## Downloads

| Platform | File |
| --- | --- |
| **Android** (phones, tablets, Android TV · Android 7.0+) | `LumenTV-<version>.apk` |

### Installing

- **Android**: download the APK on your device and open it. Allow "Install unknown apps" for your browser or file manager when asked.
  On Android TV, sideload it with a file manager or `adb install`.

## What's new in 1.9.9

- **Home appears much faster, especially on Android TV.** Building the Home rows (recommendations, on now, categories) was the slow part of "Loading channels…" once you had a watch history. It's now about 28 times faster, with exactly the same rows as before. On low-end and 32-bit TVs this was several seconds at every start, and again whenever the guide or your history updated.
- Also includes the redesigned TV channel list from 1.9.8.
