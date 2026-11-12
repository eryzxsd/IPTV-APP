// Small path-based CORS proxy used for playlists, EPG files and streams.
//
//   /proxy/<scheme>/<host[:port]>/<path>?<query>
//
// Using the path (instead of ?url=) means relative URLs inside HLS
// manifests resolve back through the proxy automatically. Absolute URLs
// inside .m3u8 manifests are rewritten so segments also go through it.

import { Readable } from 'node:stream';
import { lookup } from 'node:dns/promises';
import net from 'node:net';

const PREFIX = '/proxy/';
const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36';

function isPrivateIp(ip) {
  if (net.isIPv4(ip)) {
    const [a, b] = ip.split('.').map(Number);
    return (
      a === 10 ||
      a === 127 ||
      a === 0 ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) ||
      (a === 100 && b >= 64 && b <= 127)
    );
  }
  const v = ip.toLowerCase();
  if (v.startsWith('::ffff:')) return isPrivateIp(v.slice(7));
  return v === '::1' || v === '::' || v.startsWith('fc') || v.startsWith('fd') || v.startsWith('fe80');
}

async function assertPublicHost(hostname) {
  const host = hostname.replace(/^\[|\]$/g, '');
  if (host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.internal')) {
    throw new Error('blocked host');
  }
  const addrs = net.isIP(host) ? [{ address: host }] : await lookup(host, { all: true });
  if (addrs.some((a) => isPrivateIp(a.address))) throw new Error('blocked host');
}

export function toProxyPath(target) {
  const u = new URL(target);
  return `${PREFIX}${u.protocol.replace(':', '')}/${u.host}${u.pathname}${u.search}`;
}

function rewriteManifest(text, baseUrl) {
  const abs = (ref) => {
    try {
      return toProxyPath(new URL(ref, baseUrl).href);
    } catch {
      return ref;
    }
  };
  return text
    .split(/\r?\n/)
    .map((line) => {
      const t = line.trim();
      if (!t) return line;
      if (t.startsWith('#')) return line.replace(/URI="([^"]+)"/g, (_, u) => `URI="${abs(u)}"`);
      return abs(t);
    })
    .join('\n');
}

function setCors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Headers', '*');
  res.setHeader('Access-Control-Expose-Headers', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
}

export function createProxyHandler() {
  return async function proxy(req, res, next) {
    const rawUrl = req.url || '';
    if (!rawUrl.startsWith(PREFIX)) return next ? next() : undefined;

    setCors(res);
    if (req.method === 'OPTIONS') {
      res.statusCode = 204;
      return res.end();
    }
    if (rawUrl === `${PREFIX}health`) {
      res.setHeader('Content-Type', 'application/json');
      return res.end('{"ok":true}');
    }

    const m = rawUrl.slice(PREFIX.length).match(/^(https?)\/([^/?#]+)(.*)$/);
    if (!m) {
      res.statusCode = 400;
      return res.end('Bad proxy URL');
    }
    let target;
    try {
      target = new URL(`${m[1]}://${m[2]}${m[3] || '/'}`);
      await assertPublicHost(target.hostname);
    } catch (e) {
      res.statusCode = 403;
      return res.end(String(e.message || e));
    }

    const headers = { 'user-agent': UA, accept: '*/*' };
    if (req.headers.range) headers.range = req.headers.range;

    const ctrl = new AbortController();
    req.on('close', () => ctrl.abort());

    let upstream;
    try {
      upstream = await fetch(target, { headers, redirect: 'follow', signal: ctrl.signal });
    } catch (e) {
      if (ctrl.signal.aborted) return;
      res.statusCode = 502;
      return res.end(`Upstream error: ${e.message || e}`);
    }

    const finalUrl = upstream.url || target.href;
    const type = upstream.headers.get('content-type') || '';
    const isManifest = /mpegurl/i.test(type) || /\.m3u8(\?|$)/i.test(new URL(finalUrl).pathname);

    res.statusCode = upstream.status;
    for (const h of ['content-type', 'cache-control', 'last-modified', 'etag', 'accept-ranges', 'content-range']) {
      const v = upstream.headers.get(h);
      if (v) res.setHeader(h, v);
    }

    if (isManifest && upstream.ok) {
      const text = await upstream.text();
      // Only rewrite real HLS manifests, not plain M3U playlists of channels.
      const body = text.includes('#EXT-X-') ? rewriteManifest(text, finalUrl) : text;
      res.setHeader('content-type', 'application/vnd.apple.mpegurl');
      return res.end(body);
    }

    // fetch() already decoded any Content-Encoding, so don't forward length/encoding.
    if (!upstream.body || req.method === 'HEAD') return res.end();
    Readable.fromWeb(upstream.body)
      .on('error', () => res.destroy())
      .pipe(res);
  };
}
