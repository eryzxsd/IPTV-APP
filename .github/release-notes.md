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

## What's new in 1.4.0

- **New Home tab**, now the first screen, built around the main player:
  - The video sits at the top with all its controls (subtitles, audio, quality, more, fullscreen).
  - Below it: what's on now, Up next with the full schedule, and recommendations.
  - Picking a channel anywhere (Channels, Guide, mini player) plays it on Home.
- **Recommendations**, worked out on your phone from what you watch and your favorites (nothing is sent anywhere):
  - Rows: Recommended for you, Because you watched…, On now, Continue watching, Your favorites, and rows for
    your top categories.
  - New users get picks for their country to start with.
  - Each row brings different channels, duplicate regional feeds are merged, and offline channels are skipped.
  - When nothing is playing, Home shows a featured pick with **Watch now**.
- **Tablets and Android TV** get a wide Home: the video and its details side by side, with recommendations
  below. The fullscreen button (or Guide/Menu on a remote) goes fullscreen.
