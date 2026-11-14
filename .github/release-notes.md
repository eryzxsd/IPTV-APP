A modern IPTV player. It loads the [iptv-org](https://github.com/iptv-org/iptv) public playlist and the [epgshare01](https://epgshare01.online/) TV guide by default.

## Downloads

| Platform | File |
| --- | --- |
| **Android** (phones, tablets, Android TV · Android 7.0+) | `LumenTV-<version>.apk` |

### Installing

- **Android**: download the APK on your device and open it. Allow "Install unknown apps" for your browser or file manager when asked.
  On Android TV, sideload it with a file manager or `adb install`.

## What's new in 1.9.8

- **Android TV: a redesigned channel list** (press Left, Guide or Menu while watching), matching the phone version.
  - **A filter column** on the left: All channels, Favorites, Recently watched, Category and Country. Press **Left** to go to it and **Right** to come back. It opens on the filter you're using.
  - **Category and Country pickers** with channel counts. **Back** returns to the list, and Back again closes it.
  - Rows show **time left** for the current show and **what's on next**.
  - **Hold OK** on a channel for options: favorite, more from its category.
- **Phones and tablets:** the previous-channel card is gone from the channel list. Use **Recently watched** instead. The **Previous channel** item in the player's More menu stays.
- **Fixed:** changing the guide regions while the guide was still loading could show the wrong result. The newer load now always wins.
- Also includes the redesigned phone channel browser from 1.9.7.
