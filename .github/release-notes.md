A modern IPTV player. It loads the [iptv-org](https://github.com/iptv-org/iptv) public playlist and the [epgshare01](https://epgshare01.online/) TV guide by default.

## Downloads

| Platform | File |
| --- | --- |
| **Android** (phones, tablets, Android TV · Android 7.0+) | `LumenTV-<version>.apk` |

### Installing

- **Android**: download the APK on your device and open it. Allow "Install unknown apps" for your browser or file manager when asked.
  On Android TV, sideload it with a file manager or `adb install`.

## What's new in 1.6.0

- **A new interface made for Android TV.** On a TV the app opens a remote-friendly interface built with Android's Leanback views instead of Compose. It's fast on low-end and 32-bit TVs. Phones and tablets keep the same app as before.
  - **Home**: rows of recommendations, what's on now and favorites. The channel you're watching keeps playing behind them.
  - **Player**:
    - **OK** shows the info bar (programme, progress, what's next) and the Play, Channels, Subtitles, Audio, Quality, More and Favorite buttons.
    - **Up/Down** or **CH+/CH−** change channel.
    - **Left**, **Guide** or **Menu** opens the channel list, with what's on for every channel.
    - **Back** closes what's open, then returns to Home.
  - **TV guide**: channels on the left, the full schedule of the selected channel on the right.
  - **Search** by channel name, category or programme, with voice search from the remote.
  - **Settings**: every option from the phone app, including playback, decoder, clock, guide regions and playlists.
