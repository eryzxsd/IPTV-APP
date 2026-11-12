import { useCallback, useEffect, useRef, useState } from 'react';
import type { Channel, EpgData, EpgProgress, Settings } from './types';
import { parseM3U } from './m3u';
import { smartFetch } from './net';
import { cacheGet, cacheSet } from './idb';
import type { EpgRequest } from './epg.worker';

export function useLocalStorage<T>(key: string, initial: T) {
  const [value, setValue] = useState<T>(() => {
    try {
      const raw = localStorage.getItem(key);
      if (!raw) return initial;
      const parsed = JSON.parse(raw);
      const isObj = (v: unknown) => v && typeof v === 'object' && !Array.isArray(v);
      return isObj(initial) && isObj(parsed) ? { ...initial, ...parsed } : parsed;
    } catch {
      return initial;
    }
  });
  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {
      /* ignore */
    }
  }, [key, value]);
  return [value, setValue] as const;
}

/** A clock that ticks every `ms` so EPG progress bars stay fresh. */
export function useNow(ms = 30_000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), ms);
    return () => clearInterval(id);
  }, [ms]);
  return now;
}

const PLAYLIST_TTL = 6 * 3600_000;
const EPG_TTL = 4 * 3600_000;

export function usePlaylists(settings: Settings, proxyAvailable: boolean | null) {
  const [channels, setChannels] = useState<Channel[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);
  const force = useRef(false);

  useEffect(() => {
    if (proxyAvailable === null) return;
    const ctrl = new AbortController();
    const urls = settings.playlists.filter(Boolean);
    const key = `pl:${urls.join('|')}`;
    setLoading(true);
    setError(null);
    (async () => {
      const cached = force.current ? null : await cacheGet<Channel[]>(key, PLAYLIST_TTL);
      force.current = false;
      if (cached?.length) {
        setChannels(cached);
        setLoading(false);
        return;
      }
      const errors: string[] = [];
      const all: Channel[] = [];
      await Promise.all(
        urls.map(async (url, i) => {
          try {
            const res = await smartFetch(url, settings.proxyMode, proxyAvailable, ctrl.signal);
            all.push(...parseM3U(await res.text(), i));
          } catch (e) {
            if (!ctrl.signal.aborted) errors.push(`${url}: ${(e as Error).message}`);
          }
        }),
      );
      if (ctrl.signal.aborted) return;
      // De-duplicate identical entries (same name + stream URL).
      const seen = new Set<string>();
      const unique = all
        .sort((a, b) => a.source - b.source)
        .filter((c) => {
          const k = `${c.name}|${c.url}`;
          return seen.has(k) ? false : (seen.add(k), true);
        });
      setChannels(unique);
      setError(errors.length ? errors.join('\n') : null);
      setLoading(false);
      if (unique.length) cacheSet(key, unique);
    })();
    return () => ctrl.abort();
  }, [settings.playlists, settings.proxyMode, proxyAvailable, nonce]);

  const reload = useCallback(() => {
    force.current = true;
    setNonce((n) => n + 1);
  }, []);

  return { channels, loading, error, reload };
}

export function useEpg(channels: Channel[], settings: Settings, proxyAvailable: boolean | null) {
  const [data, setData] = useState<EpgData>({});
  const [progress, setProgress] = useState<EpgProgress>({ phase: 'idle' });
  const [nonce, setNonce] = useState(0);
  const force = useRef(false);

  useEffect(() => {
    if (!settings.epgEnabled || !channels.length || proxyAvailable === null || !settings.epgUrls.length) {
      setProgress({ phase: 'idle' });
      return;
    }
    const wantedMap = new Map<string, string>();
    for (const c of channels) if (!wantedMap.has(c.epgKey)) wantedMap.set(c.epgKey, c.name);
    const key = `epg:${settings.epgUrls.join('|')}:${wantedMap.size}`;
    let worker: Worker | null = null;
    let cancelled = false;

    (async () => {
      const cached = force.current ? null : await cacheGet<EpgData>(key, EPG_TTL);
      force.current = false;
      if (cancelled) return;
      if (cached) {
        setData(cached);
        setProgress({ phase: 'done', programmes: -1 });
        return;
      }
      setProgress({ phase: 'loading', sourceIndex: 0, sourceCount: settings.epgUrls.length, bytes: 0 });
      worker = new Worker(new URL('./epg.worker.ts', import.meta.url), { type: 'module' });
      worker.onmessage = (e) => {
        const msg = e.data;
        if (msg.type === 'progress') {
          setProgress({ phase: 'loading', ...msg });
        } else if (msg.type === 'done') {
          setData(msg.data);
          const any = Object.keys(msg.data).length > 0;
          setProgress({ phase: any || !msg.errors.length ? 'done' : 'error', programmes: msg.programmes, errors: msg.errors });
          if (any) cacheSet(key, msg.data);
          worker?.terminate();
        }
      };
      worker.onerror = (e) => setProgress({ phase: 'error', errors: [e.message || 'Worker failed'] });
      const req: EpgRequest = {
        urls: settings.epgUrls,
        wanted: [...wantedMap],
        proxyMode: settings.proxyMode,
        proxyAvailable: !!proxyAvailable,
      };
      worker.postMessage(req);
    })();

    return () => {
      cancelled = true;
      worker?.terminate();
    };
  }, [channels, settings.epgUrls, settings.epgEnabled, settings.proxyMode, proxyAvailable, nonce]);

  const reload = useCallback(() => {
    force.current = true;
    setNonce((n) => n + 1);
  }, []);

  return { data, progress, reload };
}
