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

## What's new in 1.2.1

- **Updates now install over the previous version.** The APK is signed with a permanent release key.
  If you installed 1.2.0 or older, uninstall it once before installing this version. From now on, updates install in place.

## What's new in 1.2

- **Player controls** on Android:
  - **Subtitles (CC):** choose a subtitle or closed-caption track, or turn them off
  - **Audio:** pick the language or audio track
  - **Quality:** Auto (shows the current resolution) or a fixed resolution with its bitrate
  - **More menu:** aspect ratio (Fit / Fill / Stretch), sleep timer, picture-in-picture and stream info
- **Go live:** after pausing, tap the LIVE badge to jump back to the live edge
- Your audio and subtitle language choices carry over when you change channels

## What's new in 1.1

- **New native Android app** built with Kotlin, Jetpack Compose and Material 3, with Media3/ExoPlayer for playback
  - Smaller and faster than before, and it plays streams that need special headers (user agent or referrer)
  - Adaptive layout: bottom bar on phones, side rail on tablets and Android TV
  - Mini player while browsing, picture-in-picture, fullscreen, quality picker, TV-remote channel keys

## Highlights

- 11,000+ free channels, with categories, countries, favorites, recently watched and search
- TV guide timeline, plus what's on now and next for every channel that has guide data
- Built-in proxy, so streams and guide files that browsers normally block still play
- Quality selection, picture-in-picture, fullscreen, and keyboard and TV-remote channel zapping

Many public streams are offline or geo-blocked at any given time. Channels that fail are marked **Offline**, and you can hide them.
