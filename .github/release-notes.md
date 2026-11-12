A modern IPTV player. It loads the [iptv-org](https://github.com/iptv-org/iptv) public playlist and the [epgshare01](https://epgshare01.online/) TV guide by default.

## Downloads

| Platform | File |
| --- | --- |
| **Android** (phones, tablets, Android TV · Android 7.0+) | `LumenTV-<version>.apk` |

### Installing

- **Android**: download the APK on your device and open it. Allow "Install unknown apps" for your browser or file manager when asked.
  On Android TV, sideload it with a file manager or `adb install`.

## What's new in 1.5.0

- **Play in background** (Settings → Playback): the sound keeps playing when you leave the app or lock the screen.
  - The notification has play/pause and next/previous channel.
  - Lock-screen and Bluetooth headset buttons work too.
  - On Android 13 and newer you'll be asked to allow notifications.
- **Decoder** (Settings → Playback):
  - **Auto:** hardware, falling back to software.
  - **HW:** hardware decoders only.
  - **HW+:** hardware with tunnelled playback and async decoding, lowest CPU use and best on Android TV.
  - **SW:** software decoders only.
  - Changes apply immediately, and so does Buffering now.
- **Show clock** (Settings → Playback): show the time on the player, either with the controls or always on the video.
- The **Windows app is discontinued**. New releases include only the Android APK.
