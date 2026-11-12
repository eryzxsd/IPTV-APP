import type { Settings } from './types';

export const IPTV_ORG_PLAYLISTS = [
  { label: 'iptv-org — All channels', url: 'https://iptv-org.github.io/iptv/index.m3u' },
  { label: 'iptv-org — Grouped by country', url: 'https://iptv-org.github.io/iptv/index.country.m3u' },
  { label: 'iptv-org — Grouped by category', url: 'https://iptv-org.github.io/iptv/index.category.m3u' },
  { label: 'iptv-org — Grouped by language', url: 'https://iptv-org.github.io/iptv/index.language.m3u' },
];

export const EPGSHARE_BASE = 'https://epgshare01.online/epgshare01/';
export const epgshareUrl = (code: string) => `${EPGSHARE_BASE}epg_ripper_${code}.xml.gz`;

/**
 * epgshare01 files (from https://epgshare01.online/epgshare01/).
 * Country files are labelled with their country name; the number is the
 * file variant some countries have more than one of.
 */
const COUNTRY_FILES = ['AE1', 'AL1', 'AR1', 'AT1', 'AU1', 'BA1', 'BB1', 'BE2', 'BG1', 'BR1', 'BR2', 'CA2', 'CH1', 'CL1', 'CO1', 'CR1', 'CY1', 'CZ1', 'DE1', 'DK1', 'DO1', 'EC1', 'EG1', 'ES1', 'FI1', 'FR1', 'GR1', 'HK1', 'HR1', 'HU1', 'ID1', 'IE1', 'IL1', 'IN1', 'IN2', 'IN4', 'IT1', 'JM1', 'JP1', 'JP2', 'KE1', 'KR1', 'KZ1', 'LT1', 'LU1', 'LV1', 'MN1', 'MT1', 'MX1', 'MY1', 'NG1', 'NL1', 'NO1', 'NZ1', 'PA1', 'PE1', 'PH1', 'PH2', 'PK1', 'PL1', 'PT1', 'RO1', 'RO2', 'RS1', 'SA1', 'SA2', 'SE1', 'SG1', 'SK1', 'SV1', 'TH1', 'TR1', 'TR3', 'UA1', 'UK1', 'US2', 'US_LOCALS1', 'US_SPORTS1', 'UY1', 'VN1', 'ZA1'];

const regionName = (() => {
  try {
    const dn = new Intl.DisplayNames(['en'], { type: 'region' });
    return (c: string) => dn.of(c === 'UK' ? 'GB' : c) || c;
  } catch {
    return (c: string) => c;
  }
})();

export const EPGSHARE_REGIONS: { code: string; label: string; kind: 'country' | 'service' }[] = [
  ...COUNTRY_FILES.map((code) => {
    const m = code.match(/^([A-Z]{2})(_[A-Z]+)?(\d)$/)!;
    const extra = m[2] === '_LOCALS' ? ' Locals' : m[2] === '_SPORTS' ? ' Sports' : '';
    const same = COUNTRY_FILES.filter((c) => c.startsWith(m[1]) && !c.includes('_') && !m[2]).length > 1;
    return { code, label: `${regionName(m[1])}${extra}${same ? ` ${m[3]}` : ''}`, kind: 'country' as const };
  }).sort((a, b) => a.label.localeCompare(b.label)),
  ...([['PLEX1', 'Plex'], ['DISTROTV1', 'DistroTV'], ['RAKUTEN1', 'Rakuten TV'], ['PEACOCK1', 'Peacock'], ['AMCCHANNELS1', 'AMC'], ['ALJAZEERA1', 'Al Jazeera'], ['BEIN1', 'beIN Sports'], ['VOA1', 'Voice of America'], ['TBNPLUS1', 'TBN+'], ['WHALETVPLUS1', 'Whale TV+'], ['ASIANTELEVISION1', 'Asian Television'], ['MUSICBOX1', 'Music Box'], ['DELUXEMUSIC1', 'Deluxe Music'], ['RALLY_TV1', 'Rally TV'], ['DIRECTVSPORTS1', 'DirecTV Sports'], ['ALL_SOURCES1', 'Everything (≈200 MB)']] as const).map(([code, label]) => ({ code, label, kind: 'service' as const })),
];

export const DEFAULT_SETTINGS: Settings = {
  playlists: [IPTV_ORG_PLAYLISTS[0].url],
  epgUrls: ['US2', 'UK1', 'CA2', 'AU1', 'PLEX1', 'DISTROTV1'].map(epgshareUrl),
  epgEnabled: true,
  proxyMode: 'auto',
};
