import type { Settings } from './types';

export const IPTV_ORG_PLAYLISTS = [
  { label: 'iptv-org — All channels', url: 'https://iptv-org.github.io/iptv/index.m3u' },
  { label: 'iptv-org — Grouped by country', url: 'https://iptv-org.github.io/iptv/index.country.m3u' },
  { label: 'iptv-org — Grouped by category', url: 'https://iptv-org.github.io/iptv/index.category.m3u' },
  { label: 'iptv-org — Grouped by language', url: 'https://iptv-org.github.io/iptv/index.language.m3u' },
];

export const EPGSHARE_BASE = 'https://epgshare01.online/epgshare01/';
export const epgshareUrl = (code: string) => `${EPGSHARE_BASE}epg_ripper_${code}.xml.gz`;

/** A selection of epgshare01 region files. */
export const EPGSHARE_REGIONS: { code: string; label: string }[] = [
  { code: 'US1', label: 'United States' },
  { code: 'US_LOCALS2', label: 'US Locals' },
  { code: 'UK1', label: 'United Kingdom' },
  { code: 'CA1', label: 'Canada' },
  { code: 'AU1', label: 'Australia' },
  { code: 'NZ1', label: 'New Zealand' },
  { code: 'IE1', label: 'Ireland' },
  { code: 'IN1', label: 'India' },
  { code: 'DE1', label: 'Germany' },
  { code: 'FR1', label: 'France' },
  { code: 'IT1', label: 'Italy' },
  { code: 'ES1', label: 'Spain' },
  { code: 'PT1', label: 'Portugal' },
  { code: 'NL1', label: 'Netherlands' },
  { code: 'BE1', label: 'Belgium' },
  { code: 'CH1', label: 'Switzerland' },
  { code: 'PL1', label: 'Poland' },
  { code: 'SE1', label: 'Sweden' },
  { code: 'NO1', label: 'Norway' },
  { code: 'DK1', label: 'Denmark' },
  { code: 'FI1', label: 'Finland' },
  { code: 'TR1', label: 'Turkey' },
  { code: 'GR1', label: 'Greece' },
  { code: 'BR1', label: 'Brazil' },
  { code: 'MX1', label: 'Mexico' },
  { code: 'AR1', label: 'Argentina' },
  { code: 'ZA1', label: 'South Africa' },
  { code: 'PH1', label: 'Philippines' },
  { code: 'MY1', label: 'Malaysia' },
  { code: 'SG1', label: 'Singapore' },
  { code: 'ID1', label: 'Indonesia' },
  { code: 'JP1', label: 'Japan' },
  { code: 'KR1', label: 'South Korea' },
  { code: 'ALL_SOURCES1', label: 'Everything (very large)' },
];

export const DEFAULT_SETTINGS: Settings = {
  playlists: [IPTV_ORG_PLAYLISTS[0].url],
  epgUrls: ['US1', 'UK1', 'CA1', 'AU1'].map(epgshareUrl),
  epgEnabled: true,
  proxyMode: 'auto',
};
