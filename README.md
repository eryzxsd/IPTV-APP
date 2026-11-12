# Lumen TV

A modern web IPTV player. It loads the **[iptv-org](https://github.com/iptv-org/iptv)** public playlist and the
**[epgshare01](https://epgshare01.online/)** TV guide by default.

## Features

- **Default sources**: `https://iptv-org.github.io/iptv/index.m3u` for channels, and these epgshare01 XMLTV files:
  US, UK, Canada, Australia, Plex and DistroTV (about 24 MB in total). You can turn on any of the 80+ country files
  and 15 service files in Settings.
- **Guide matching**: epgshare01 uses its own channel IDs (e.g. `CNBC.HD.us2`), so channels are matched by name.
  Exact names and the same country score higher, and East-coast feeds are preferred over Pacific or +1 feeds.
  Each channel gets the single best guide match.
- **Dead stream marking**: channels whose stream failed are marked *Offline* and can be hidden. Resolution,
  geo-blocked and part-time tags from iptv-org show as small badges.
- **Live view**: a channel list that stays fast with 10k+ channels, with logos, flags, what's on now and a progress bar.
  Next to it are the player and a now/next panel with the day's schedule.
- **TV guide**: a timeline grid with a "now" line, sticky channel column, show details on hover, and click to watch.
- **Player**: hls.js with quality selection, picture-in-picture, fullscreen, volume memory, and auto-retry through the proxy.
- **Browse**: categories, countries (with search), favorites, recently watched, and search across channel names,
  categories and the shows on now.
- **Keyboard**: <kbd>↑</kbd>/<kbd>↓</kbd> change channel, <kbd>Space</kbd> play/pause, <kbd>F</kbd> fullscreen, <kbd>M</kbd> mute, <kbd>/</kbd> search.
- **Responsive**: works on phones, with a slide-out menu.
- **Caching**: the parsed playlist and guide are cached in IndexedDB, so reloads are instant.
  The guide is streamed, un-gzipped and parsed in a Web Worker, and only shows for your channels are kept.

## Download

Get the Android APK (phones, tablets, Android TV) from the
[Releases](https://github.com/eryzxsd/IPTV-APP/releases) page.

## Run it

```bash
npm install
npm run dev        # development, http://localhost:5173
# or
npm run build
npm start          # production server on http://localhost:8080 (PORT=xxxx to change)
```

## Why there's a small server

Browsers block many IPTV streams and the epgshare01 files because of CORS rules. They also block
`http://` streams on `https://` pages. Both `npm run dev` and `npm start` include a small proxy at `/proxy/...`.
The app uses it on its own when a direct request fails (Settings → Proxy: Auto / Always / Never).
The proxy rewrites HLS manifests so video segments go through it too. It refuses private and local network addresses.

Behind a corporate HTTP proxy, start the server with `NODE_USE_ENV_PROXY=1 npm start` so Node uses `HTTPS_PROXY`.

You can host `dist/` as a plain static site. Without the proxy, only CORS-enabled sources will load.

## Apps

| Platform | How it works | Build locally |
| --- | --- | --- |
| Windows / macOS / Linux | **Discontinued**, no longer released. The Electron shell (`desktop/main.mjs`) is still in the repo and can be built with `npm run build:win` | n/a |
| Android & Android TV | Native app in Kotlin + Jetpack Compose + Material 3, with Media3/ExoPlayer for playback (`android/`) | `cd android && ./gradlew assembleRelease` (needs the Android SDK, platform 37) |

To publish a release, open **Actions → Release → Run workflow** (or push a `v*` tag). It builds the APK on Linux
and attaches it to a GitHub release. The version comes from `package.json`.
The release text comes from `.github/release-notes.md`; replace its "What's new" section for each version.

### Android signing

Every APK is signed with the same release key, so new versions install as updates over old ones. The release workflow:

- signs with the keystore from these repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`
  and `ANDROID_KEY_ALIAS`
- **refuses to release** when those secrets are missing, instead of falling back to a throwaway key
- checks that the APK's certificate matches `android/signing-cert-sha256.txt`

Keep a backup of the keystore (`.jks`) and its password somewhere safe. If you lose them, you can't publish updates,
and everyone would have to uninstall and reinstall.

### Android app

The Android app is fully native and doesn't use the web code:

- **UI**: Jetpack Compose and Material 3. It uses a bottom bar on phones and a navigation rail on tablets and TV.
  - **Home** holds the main player (auto-hiding controls, subtitles, audio, quality, fullscreen channel browser), what's on,
    and on-device recommendations (`data/Recommender.kt`).
  - **Channels** has category and country sheets, **Guide** is a TV guide grid, and **Settings** covers every option.
- **Playback**: Media3/ExoPlayer, which plays HLS directly. No CORS proxy is needed, and it sends the user agent and
  referrer some iptv-org streams require.
- **Data**: the same M3U parsing and epgshare01 matching as the web app (`data/M3uParser.kt`, `data/XmltvParser.kt`),
  with results cached on disk. Settings, favorites, recents and stream health are kept in DataStore.
- **Tests**: `./gradlew testDebugUnitTest` runs the parser tests. Two optional extras:
  - `LUMEN_REAL_DATA=<folder>` also runs them against real downloads.
  - `LUMEN_SCREENSHOTS=<folder>` renders the real app with Robolectric and saves screenshots.

## Project layout

```
server/proxy.mjs        CORS proxy (shared by Vite dev server and production server)
server/app.mjs          static server + proxy (shared by npm start and the desktop app)
server/index.mjs        `npm start` entry point
desktop/main.mjs        Electron main process (discontinued Windows/macOS/Linux app)
android/                Android / Android TV app (Kotlin, Compose, Material 3, Media3)
.github/workflows/      release workflow (Android APK)
src/lib/m3u.ts          M3U parser
src/lib/epg.worker.ts   streaming XMLTV(.gz) parser in a Web Worker
src/lib/hooks.ts        playlist / EPG loading, caching, local storage
src/components/         Player, Guide, Sidebar, ChannelRow, Schedule, Settings…
```
