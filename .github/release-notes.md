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

## What's new in 1.2.3

- **Clearer quality names:**
  - Sizes use familiar names (a 1216×684 stream is **720p**, not "684p").
  - Streams that don't state their resolutions now show **Highest / High / Medium / Low / Lowest** with the bitrate
    underneath, instead of bare bitrates. Each switches to its real resolution once it has played.
- **"Playing now"** now marks the quality Auto is actually using.
- Bitrates are always written with a dot (3.3 Mbps).
