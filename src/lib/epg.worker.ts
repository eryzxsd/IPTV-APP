/// <reference lib="webworker" />
// Streams XMLTV files (optionally gzipped), keeps only programmes for the
// channels in the playlist, within a time window, and posts the result back.
import type { EpgData, Programme, ProxyMode } from './types';
import { smartFetch } from './net';
import { normId, normName } from './m3u';

export interface EpgRequest {
  urls: string[];
  /** epgKey -> channel name, for every channel in the playlist */
  wanted: [string, string][];
  proxyMode: ProxyMode;
  proxyAvailable: boolean;
}

const BACK = 3 * 3600_000;
const AHEAD = 48 * 3600_000;

const post = (msg: unknown) => (self as unknown as DedicatedWorkerGlobalScope).postMessage(msg);

self.onmessage = async (e: MessageEvent<EpgRequest>) => {
  const { urls, wanted, proxyMode, proxyAvailable } = e.data;
  const ids = new Set<string>();
  const byName = new Map<string, string[]>();
  for (const [key, name] of wanted) {
    ids.add(key);
    const n = key.startsWith('n:') ? key.slice(2) : normName(name);
    if (!n) continue;
    const arr = byName.get(n);
    if (arr) {
      if (!arr.includes(key)) arr.push(key);
    } else byName.set(n, [key]);
  }

  const data: EpgData = {};
  const errors: string[] = [];
  let total = 0;
  const now = Date.now();
  const from = now - BACK;
  const to = now + AHEAD;

  for (let i = 0; i < urls.length; i++) {
    const url = urls[i];
    let bytes = 0;
    let lastPost = 0;
    const report = () =>
      post({ type: 'progress', source: url, sourceIndex: i, sourceCount: urls.length, bytes, programmes: total });
    report();
    try {
      const res = await smartFetch(url, proxyMode, proxyAvailable);
      if (!res.body) throw new Error('empty response');
      const counted = res.body.pipeThrough(
        new TransformStream<Uint8Array, Uint8Array>({
          transform(chunk, ctrl) {
            bytes += chunk.byteLength;
            ctrl.enqueue(chunk);
          },
        }),
      );
      const text = (await maybeGunzip(counted)).pipeThrough(new TextDecoderStream() as unknown as TransformStream<Uint8Array, string>);
      const mapping = new Map<string, string[]>(); // xmltv channel id -> epgKeys
      const reader = text.getReader();
      let buf = '';
      const re = /<(channel|programme)\b([^>]*)>([\s\S]*?)<\/\1>/g;
      for (;;) {
        const { value, done } = await reader.read();
        if (value) buf += value;
        re.lastIndex = 0;
        let m: RegExpExecArray | null;
        let end = 0;
        while ((m = re.exec(buf))) {
          end = re.lastIndex;
          const attrs = m[2];
          if (m[1] === 'channel') {
            const id = attr(attrs, 'id');
            if (!id) continue;
            const keys = new Set<string>();
            const k = normId(id);
            if (ids.has(k)) keys.add(k);
            for (const dn of m[3].matchAll(/<display-name[^>]*>([\s\S]*?)<\/display-name>/g)) {
              const n = normName(decode(dn[1]));
              const ks = byName.get(n);
              if (ks) ks.forEach((x) => keys.add(x));
              if (ids.has(`n:${n}`)) keys.add(`n:${n}`);
            }
            if (keys.size) mapping.set(id, [...keys]);
          } else {
            const keys = mapping.get(attr(attrs, 'channel')) ?? matchLoose(attr(attrs, 'channel'), ids, mapping);
            if (!keys || !keys.length) continue;
            const start = parseTime(attr(attrs, 'start'));
            const stop = parseTime(attr(attrs, 'stop'));
            if (!start || !stop || stop < from || start > to) continue;
            const p = parseProgramme(m[3], start, stop);
            for (const k of keys) (data[k] ||= []).push(p);
            total++;
          }
        }
        if (end) buf = buf.slice(end);
        // Avoid unbounded growth if there is junk with no closing tag.
        if (buf.length > 4_000_000) buf = buf.slice(-1_000_000);
        if (Date.now() - lastPost > 250) {
          lastPost = Date.now();
          report();
        }
        if (done) break;
      }
      report();
    } catch (err) {
      errors.push(`${shortName(url)}: ${(err as Error).message || err}`);
    }
  }

  for (const k in data) {
    const list = data[k].sort((a, b) => a.start - b.start);
    // drop duplicates (same channel in several sources)
    data[k] = list.filter((p, i) => i === 0 || p.start !== list[i - 1].start);
  }
  post({ type: 'done', data, errors, programmes: total });
};

/** Programmes for channels whose <channel> entry was missing. */
function matchLoose(id: string, ids: Set<string>, mapping: Map<string, string[]>): string[] | undefined {
  if (!id) return undefined;
  const k = normId(id);
  const res = ids.has(k) ? [k] : undefined;
  mapping.set(id, res ?? []);
  return res;
}

async function maybeGunzip(stream: ReadableStream<Uint8Array>): Promise<ReadableStream<Uint8Array>> {
  const reader = stream.getReader();
  const first = await reader.read();
  const head = first.value;
  const rest = new ReadableStream<Uint8Array>({
    start(ctrl) {
      if (head) ctrl.enqueue(head);
      if (first.done) ctrl.close();
    },
    async pull(ctrl) {
      const { value, done } = await reader.read();
      if (done) ctrl.close();
      else ctrl.enqueue(value);
    },
    cancel(r) {
      reader.cancel(r);
    },
  });
  const gz = head && head.length > 1 && head[0] === 0x1f && head[1] === 0x8b;
  return gz ? rest.pipeThrough(new DecompressionStream('gzip') as unknown as TransformStream<Uint8Array, Uint8Array>) : rest;
}

function attr(s: string, name: string): string {
  const m = s.match(new RegExp(`\\b${name}="([^"]*)"`));
  return m ? decode(m[1]) : '';
}

function tag(s: string, name: string): string | undefined {
  const m = s.match(new RegExp(`<${name}\\b[^>]*>([\\s\\S]*?)</${name}>`));
  return m ? decode(m[1]).trim() : undefined;
}

function parseProgramme(body: string, start: number, stop: number): Programme {
  const p: Programme = { start, stop, title: tag(body, 'title') || 'Untitled' };
  const desc = tag(body, 'desc');
  if (desc) p.desc = desc.length > 600 ? `${desc.slice(0, 600)}…` : desc;
  const cat = tag(body, 'category');
  if (cat) p.category = cat;
  const icon = body.match(/<icon\b[^>]*src="([^"]+)"/);
  if (icon) p.icon = decode(icon[1]);
  const ep = body.match(/<episode-num\b[^>]*system="onscreen"[^>]*>([\s\S]*?)<\/episode-num>/);
  if (ep) p.episode = decode(ep[1]).trim();
  else {
    const sub = tag(body, 'sub-title');
    if (sub) p.episode = sub;
  }
  return p;
}

/** XMLTV time: YYYYMMDDhhmmss +hhmm */
function parseTime(s: string): number {
  const m = s.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-])?(\d{2})?(\d{2})?/);
  if (!m) return 0;
  const t = Date.UTC(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0));
  const off = m[7] ? (m[7] === '-' ? -1 : 1) * ((+(m[8] || 0)) * 60 + +(m[9] || 0)) * 60000 : 0;
  return t - off;
}

const ENT: Record<string, string> = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };
function decode(s: string): string {
  if (s.startsWith('<![CDATA[')) return s.slice(9, s.lastIndexOf(']]>'));
  return s.replace(/&(#x[0-9a-f]+|#\d+|\w+);/gi, (all, e: string) => {
    if (e[0] === '#') {
      const code = e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
      return Number.isFinite(code) ? String.fromCodePoint(code) : all;
    }
    return ENT[e.toLowerCase()] ?? all;
  });
}

function shortName(url: string) {
  return url.split('/').pop() || url;
}
