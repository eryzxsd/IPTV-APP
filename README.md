# Lumen TV

A modern web IPTV player. It loads the **[iptv-org](https://github.com/iptv-org/iptv)** public playlist and the
**[epgshare01](https://epgshare01.online/)** TV guide by default.

## Features

- **Default sources**: `https://iptv-org.github.io/iptv/index.m3u` for channels, and epgshare01 XMLTV files
  (US, UK, CA and AU to start; you can pick 30+ regions in Settings).
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

You can host `dist/` as a plain static site. Without the proxy, only CORS-enabled sources will load.

## Project layout

```
server/proxy.mjs        CORS proxy (shared by Vite dev server and production server)
server/index.mjs        production static server + proxy
src/lib/m3u.ts          M3U parser
src/lib/epg.worker.ts   streaming XMLTV(.gz) parser in a Web Worker
src/lib/hooks.ts        playlist / EPG loading, caching, local storage
src/components/         Player, Guide, Sidebar, ChannelRow, Schedule, Settings…
```
