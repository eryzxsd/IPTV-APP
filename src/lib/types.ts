export interface Channel {
  /** Unique, stable id (derived from tvg-id/name + url). */
  id: string;
  name: string;
  logo: string;
  groups: string[];
  tvgId: string;
  /** Key used to join with EPG data. */
  epgKey: string;
  /** ISO 3166 alpha-2 country code (upper case) or '' */
  country: string;
  url: string;
  /** e.g. 1080p */
  quality?: string;
  geoBlocked?: boolean;
  not247?: boolean;
  /** Source playlist index */
  source: number;
}

export interface Programme {
  start: number;
  stop: number;
  title: string;
  desc?: string;
  category?: string;
  icon?: string;
  episode?: string;
}

export type EpgData = Record<string, Programme[]>;

export type ProxyMode = 'auto' | 'always' | 'never';

export interface Settings {
  playlists: string[];
  epgUrls: string[];
  epgEnabled: boolean;
  proxyMode: ProxyMode;
}

export interface EpgProgress {
  phase: 'idle' | 'loading' | 'done' | 'error';
  source?: string;
  sourceIndex?: number;
  sourceCount?: number;
  bytes?: number;
  programmes?: number;
  errors?: string[];
}
