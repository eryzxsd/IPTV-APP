import type { Channel } from './types';
import { countryFromTvgId } from './countries';

const ATTR_RE = /([\w-]+)="([^"]*)"/g;

export function normId(id: string): string {
  return id.trim().toLowerCase().replace(/@.*$/, '');
}

export function normName(name: string): string {
  return name
    .toLowerCase()
    .replace(/\([^)]*\)|\[[^\]]*\]/g, ' ')
    .replace(/\b(hd|fhd|uhd|sd|4k|hevc|h265|backup|geo-?blocked|not 24\/7)\b/g, ' ')
    .replace(/[^a-z0-9]+/g, '');
}

function hash(s: string): string {
  let h = 5381;
  for (let i = 0; i < s.length; i++) h = ((h << 5) + h + s.charCodeAt(i)) | 0;
  return (h >>> 0).toString(36);
}

/** Parses an extended M3U playlist into channels. */
export function parseM3U(text: string, source = 0): Channel[] {
  const lines = text.split(/\r?\n/);
  const out: Channel[] = [];
  let info: { attrs: Record<string, string>; name: string } | null = null;

  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith('#EXTINF')) {
      const comma = findTitleComma(line);
      const head = line.slice(0, comma);
      const name = line.slice(comma + 1).trim();
      const attrs: Record<string, string> = {};
      for (const m of head.matchAll(ATTR_RE)) attrs[m[1].toLowerCase()] = m[2];
      info = { attrs, name };
      continue;
    }
    if (line.startsWith('#')) continue;
    if (!info) continue;

    const { attrs } = info;
    const name = (info.name || attrs['tvg-name'] || 'Unknown').trim();
    const tvgId = (attrs['tvg-id'] || '').trim();
    const groups = (attrs['group-title'] || '')
      .split(';')
      .map((g) => g.trim())
      .filter((g) => g && g.toLowerCase() !== 'undefined');
    const epgKey = tvgId ? normId(tvgId) : `n:${normName(name)}`;
    out.push({
      id: hash(`${source}|${tvgId || name}|${line}`),
      name,
      logo: attrs['tvg-logo'] || '',
      groups: groups.length ? groups : ['Other'],
      tvgId,
      epgKey,
      country: countryFromTvgId(tvgId),
      url: line,
      source,
    });
    info = null;
  }
  return out;
}

/** The title is after the first comma that is not inside a quoted attribute. */
function findTitleComma(line: string): number {
  let inQuote = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') inQuote = !inQuote;
    else if (c === ',' && !inQuote) return i;
  }
  return line.length;
}
