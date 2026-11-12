import type { Programme } from './types';

const timeFmt = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' });
const dayFmt = new Intl.DateTimeFormat(undefined, { weekday: 'short', day: 'numeric', month: 'short' });

export const fmtTime = (t: number) => timeFmt.format(t);
export const fmtDay = (t: number) => dayFmt.format(t);

export function fmtDuration(ms: number): string {
  const m = Math.round(ms / 60000);
  if (m < 60) return `${m}m`;
  const h = Math.floor(m / 60);
  return m % 60 ? `${h}h ${m % 60}m` : `${h}h`;
}

/** Binary search for the programme airing at `now`. */
export function nowIndex(list: Programme[] | undefined, now: number): number {
  if (!list || !list.length) return -1;
  let lo = 0;
  let hi = list.length - 1;
  while (lo <= hi) {
    const mid = (lo + hi) >> 1;
    const p = list[mid];
    if (p.stop <= now) lo = mid + 1;
    else if (p.start > now) hi = mid - 1;
    else return mid;
  }
  return -1;
}

export function nowNext(list: Programme[] | undefined, now: number): { now?: Programme; next?: Programme } {
  if (!list?.length) return {};
  const i = nowIndex(list, now);
  if (i >= 0) return { now: list[i], next: list[i + 1] };
  const next = list.find((p) => p.start > now);
  return { next };
}

export const progress = (p: Programme, now: number) =>
  Math.min(100, Math.max(0, ((now - p.start) / (p.stop - p.start)) * 100));
