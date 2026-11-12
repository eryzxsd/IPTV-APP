A modern IPTV player. It loads the [iptv-org](https://github.com/iptv-org/iptv) public playlist and the [epgshare01](https://epgshare01.online/) TV guide by default.

## Downloads

| Platform | File |
| --- | --- |
| **Android** (phones, tablets, Android TV · Android 7.0+) | `LumenTV-<version>.apk` |
| **Windows** installer (x64) | `LumenTV-Setup-<version>.exe` |
| **Windows** portable (x64, no install) | `LumenTV-Portable-<version>.exe` |

### Installing

- **Android**: download the APK on your device and open it. Allow "Install unknown apps" for your browser or file manager when asked.
  On Android TV, sideload it with a file manager or `adb install`.
- **Windows**: the app isn't code-signed, so SmartScreen may warn you. Click **More info → Run anyway**.

## What's new in 1.4.2

- **Retry for TV guide downloads:**
  - Guide files that hit a network error, a timeout or a server error (5xx) are retried automatically, up to 3 times.
    This mostly matters for the large "Everything" file.
  - If a file still fails, the Guide tab and Settings list it with the reason and a **Retry** button.
  - Retry downloads only the failed files; the guide data you already have stays.
- **"Unsaved changes" is always visible:** the Save / Discard bar for playlist and guide-source changes is pinned
  to the bottom of Settings, so you don't have to scroll to find it.
