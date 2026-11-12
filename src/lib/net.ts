import type { ProxyMode } from './types';

let proxyAvailable: Promise<boolean> | null = null;

/** Detects whether the bundled proxy (dev server / `npm start`) is present. */
export function detectProxy(): Promise<boolean> {
  if (!proxyAvailable) {
    proxyAvailable = fetch('/proxy/health', { cache: 'no-store' })
      .then((r) => (r.ok ? r.json() : null))
      .then((j) => Boolean(j && j.ok))
      .catch(() => false);
  }
  return proxyAvailable;
}

export function proxied(url: string): string {
  try {
    const u = new URL(url);
    if (u.protocol !== 'http:' && u.protocol !== 'https:') return url;
    return `${location.origin}/proxy/${u.protocol.replace(':', '')}/${u.host}${u.pathname}${u.search}`;
  } catch {
    return url;
  }
}

/** Should a resource be loaded through the proxy right away? */
export function preferProxy(url: string, mode: ProxyMode, available: boolean): boolean {
  if (!available || mode === 'never') return false;
  if (mode === 'always') return true;
  // auto: mixed content can never work directly from an https page.
  return location.protocol === 'https:' && url.startsWith('http:');
}

/** Fetch with automatic proxy fallback (used for playlists & EPG files). */
export async function smartFetch(url: string, mode: ProxyMode, available: boolean, signal?: AbortSignal) {
  const canProxy = available && mode !== 'never';
  if (!preferProxy(url, mode, available)) {
    try {
      const r = await fetch(url, { signal });
      if (r.ok || !canProxy) {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r;
      }
    } catch (e) {
      if (!canProxy || signal?.aborted) throw e;
    }
  }
  const r = await fetch(proxied(url), { signal });
  if (!r.ok) throw new Error(`HTTP ${r.status}`);
  return r;
}
