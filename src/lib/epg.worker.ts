/// <reference lib="webworker" />
// Streams XMLTV files (optionally gzipped), keeps only programmes for the
// channels in the playlist, within a time window, and posts the result back.
import type { EpgData, Programme, ProxyMode } from './types';
import { smartFetch } from './net';
import { normId, normName } from './m3u';

export interface EpgRequest {
  urls: string[];
  /** [epgKey, channel name, country] for every distinct channel in the playlist */
  wanted: [string, string, string][];
  proxyMode: ProxyMode;
  proxyAvailable: boolean;
}

const BACK = 3 * 3600_000;
const AHEAD = 48 * 3600_000;

const post = (msg: unknown) => (self as unknown as DedicatedWorkerGlobalScope).postMessage(msg);

/** lower-case alphanumerics only, keeping everything (used for "exact" name matches) */
const loose = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '');
const ALT_FEED = /\b(pacific|west|mountain|central|east\s*\+|\+\s*1|plus\s*1|timeshift)\b|\+1/i;

/** epgshare01 ids look like `CNBC.HD.us2` / `TLC.HD.uk` — returns [name part, country]. */
function splitEpgId(id: string): [string, string] {
  const m = id.match(/^(.*)\.([a-z]{2})\d*$/i);
  if (!m) return [id, ''];
  const c = m[2].toUpperCase();
  return [m[1].replace(/\./g, ' '), c === 'UK' ? 'GB' : c];
}

interface Best {
  src: number;
  id: string;
  score: number;
}

self.onmessage = async (e: MessageEvent<EpgRequest>) => {
  const { urls, wanted, proxyMode, proxyAvailable } = e.data;
  const info = new Map<string, { name: string; loose: string; country: string }>();
  const byName = new Map<string, string[]>();
  for (const [key, name, country] of wanted) {
    const base = name.replace(/\(\d+p\)|\[[^\]]*\]/gi, '');
    info.set(key, { name, loose: loose(base), country });
    const n = key.startsWith('n:') ? key.slice(2) : normName(name);
    if (!n) continue;
    const arr = byName.get(n);
    if (arr) {
      if (!arr.includes(key)) arr.push(key);
    } else byName.set(n, [key]);
  }

  // For every playlist channel we keep exactly one EPG channel: the best match.
  const best = new Map<string, Best>();
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
      // xmltv channel id -> playlist keys this channel won in this file
      const winners = new Map<string, string[]>();
      let channelsDone = false;
      const reader = text.getReader();
      let buf = '';
      const re = /<(channel|programme)\b([^>]*)>([\s\S]*?)<\/\1>/g;

      const consider = (key: string, id: string, score: number) => {
        const cur = best.get(key);
        if (score < 40 || (cur && cur.score >= score)) return;
        best.set(key, { src: i, id, score });
      };

      const finishChannels = () => {
        channelsDone = true;
        for (const [key, b] of best) {
          if (b.src !== i) continue;
          // A better match from this file replaces data gathered from earlier files.
          delete data[key];
          const arr = winners.get(b.id);
          if (arr) arr.push(key);
          else winners.set(b.id, [key]);
        }
      };

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
            const k = normId(id);
            if (info.has(k)) consider(k, id, 100);
            const [idName, epgCountry] = splitEpgId(id);
            const names = [...m[3].matchAll(/<display-name[^>]*>([\s\S]*?)<\/display-name>/g)].map((d) => decode(d[1]));
            names.push(idName);
            for (const dn of names) {
              const n = normName(dn);
              const keys = [...(byName.get(n) || [])];
              if (info.has(`n:${n}`)) keys.push(`n:${n}`);
              for (const key of keys) {
                const ch = info.get(key)!;
                let score = 50;
                if (loose(dn) === ch.loose) score += 10;
                if (epgCountry && ch.country) score += epgCountry === ch.country ? 15 : -20;
                if (ALT_FEED.test(dn) && !ALT_FEED.test(ch.name)) score -= 15;
                consider(key, id, score);
              }
            }
          } else {
            if (!channelsDone) finishChannels();
            const keys = winners.get(attr(attrs, 'channel'));
            if (!keys) continue;
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
    data[k] = list.filter((p, i) => i === 0 || p.start !== list[i - 1].start);
    if (!data[k].length) delete data[k];
  }
  post({ type: 'done', data, errors, programmes: total });
};

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
