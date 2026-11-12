const FIX: Record<string, string> = { uk: 'GB' };

let displayNames: Intl.DisplayNames | null = null;
try {
  displayNames = new Intl.DisplayNames(['en'], { type: 'region' });
} catch {
  displayNames = null;
}

/** iptv-org tvg-ids look like `CNN.us@SD` — the suffix is the country. */
export function countryFromTvgId(tvgId: string): string {
  const m = tvgId.replace(/@.*$/, '').match(/\.([a-z]{2})$/i);
  if (!m) return '';
  const c = m[1].toLowerCase();
  return FIX[c] || c.toUpperCase();
}

export function countryName(code: string): string {
  if (!code) return 'International';
  try {
    return displayNames?.of(code) || code;
  } catch {
    return code;
  }
}

export function flag(code: string): string {
  if (!/^[A-Z]{2}$/.test(code)) return '🌐';
  return String.fromCodePoint(...[...code].map((c) => 0x1f1e6 + c.charCodeAt(0) - 65));
}
