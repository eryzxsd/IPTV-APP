A modern IPTV player. It loads the [iptv-org](https://github.com/iptv-org/iptv) public playlist and the [epgshare01](https://epgshare01.online/) TV guide by default.

## Downloads

| Platform | File |
| --- | --- |
| **Android** (phones, tablets, Android TV · Android 7.0+) | `LumenTV-<version>.apk` |

### Installing

- **Android**: download the APK on your device and open it. Allow "Install unknown apps" for your browser or file manager when asked.
  On Android TV, sideload it with a file manager or `adb install`.

## What's new in 1.7.0

- **The Android TV interface now uses Jetpack Compose and Material 3**, the same as the phone app, instead of Leanback views. Phones and tablets are unchanged.
  - **Home**: what's playing (with its programme and what's next), then rows of recommendations and what's on now. The channel you're watching keeps playing behind them.
  - **Player**:
    - **OK** shows the info bar and the Play, Channels, Subtitles, Audio, Quality, More and Favorite buttons.
    - **Up/Down** or **CH+/CH−** change channel.
    - **Left**, **Guide** or **Menu** opens the channel list.
    - **Back** closes what's open, then returns to Home.
  - **TV guide**: channels on the left, the schedule of the selected channel on the right.
  - **Search** by channel, category or programme, with voice search.
  - **Settings**: the same options as the phone app, with a clear highlight on the focused option.
  - The focused card or button always has a white outline, so it's easy to see where you are.
