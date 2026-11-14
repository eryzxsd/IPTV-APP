A modern IPTV player. It loads the [iptv-org](https://github.com/iptv-org/iptv) public playlist and the [epgshare01](https://epgshare01.online/) TV guide by default.

## Downloads

| Platform | File |
| --- | --- |
| **Android** (phones, tablets, Android TV · Android 7.0+) | `LumenTV-<version>.apk` |

### Installing

- **Android**: download the APK on your device and open it. Allow "Install unknown apps" for your browser or file manager when asked.
  On Android TV, sideload it with a file manager or `adb install`.

## What's new in 1.9.3

- **Much faster start, especially on Android TV.** "Loading channels…" no longer makes you wait every time you open the app.
  - The channel list you already have shows straight away. When it's due a refresh (every 6 hours), the new one downloads in the background instead of making you wait.
  - The TV guide works the same way: the saved guide shows at once and refreshes in the background when it's due. If a refresh fails, you keep the guide you have.
  - Saved channels and guide data now use a compact format that reads several times faster than before, which helps most on low-end and 32-bit TVs.
  - They're kept in app storage instead of the system cache, so Android no longer deletes them when storage runs low, which forced a full download.
  - Updating the playlist no longer throws away the saved TV guide.
- The first start after installing this update still downloads everything once.
