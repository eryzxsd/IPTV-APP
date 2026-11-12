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

## What's new in 1.3.0

- **Real resolutions in the quality menu.** For streams that don't label their qualities, the app reads a small sample of
  each one when you open Quality, so you see 720p / 576p / 480p / 360p / 240p instead of rank names or bitrates.
  It also works with encrypted (AES-128) streams such as Pluto TV. You can turn it off in Settings.
- **Browse channels in fullscreen:**
  - Tap the list button, swipe in from the left edge, or press Guide/Menu on a TV remote to open a channel panel
    over the video, with search, Favorites and Recent.
  - Swipe up or down on the video to change channel.
- **Settings now cover everything:**
  - **Playback:** maximum quality, buffering, subtitles on by default, default aspect ratio, picture-in-picture
    when leaving, resume last channel, look up resolutions.
  - **Channels:** sort A–Z, hide offline channels.
  - **TV guide:** refresh interval, plus the existing guide and region options.
  - **Appearance:** Material You colors (Android 12+), pure black background.
  - **Data & storage:** refresh playlist or guide, clear cache, clear watch history, reset offline marks,
    clear favorites, reset all settings.
