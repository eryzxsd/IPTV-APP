import { useEffect, useMemo, useRef, useState } from 'react';
import type { Channel, EpgData, EpgProgress, Programme } from '../lib/types';
import { fmtDay, fmtTime, fmtDuration } from '../lib/time';
import { ChannelLogo } from './Logo';
import { Icon } from './Icon';

interface Props {
  channels: Channel[];
  epg: EpgData;
  epgProgress: EpgProgress;
  clock: number;
  activeId: string | null;
  onPlay: (c: Channel) => void;
}

const PPM = 5; // pixels per minute
const ROW = 64;
const HEAD = 44;
const COL = 220;
const SLOT = 30;

export function Guide({ channels, epg, epgProgress, clock, activeId, onPlay }: Props) {
  const el = useRef<HTMLDivElement>(null);
  const [view, setView] = useState({ top: 0, left: 0, w: 1200, h: 800 });
  const [onlyEpg, setOnlyEpg] = useState(true);
  const [hover, setHover] = useState<{ p: Programme; c: Channel; x: number; y: number } | null>(null);

  const start = useMemo(() => {
    const d = new Date(clock);
    d.setMinutes(d.getMinutes() < 30 ? 0 : 30, 0, 0);
    return d.getTime() - 60 * 60000;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [Math.floor(clock / 3600_000)]);
  const end = start + 26 * 3600_000;
  const width = ((end - start) / 60000) * PPM;
  const x = (t: number) => ((t - start) / 60000) * PPM;

  const rows = useMemo(() => (onlyEpg ? channels.filter((c) => epg[c.epgKey]?.length) : channels), [channels, epg, onlyEpg]);

  const scrollToNow = () => {
    if (el.current) el.current.scrollLeft = Math.max(0, x(Date.now()) - 120);
  };

  useEffect(() => {
    const node = el.current;
    if (!node) return;
    const ro = new ResizeObserver(() => setView((v) => ({ ...v, w: node.clientWidth, h: node.clientHeight })));
    ro.observe(node);
    scrollToNow();
    return () => ro.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const firstRow = Math.max(0, Math.floor((view.top - HEAD) / ROW) - 4);
  const lastRow = Math.min(rows.length, Math.ceil((view.top + view.h) / ROW) + 4);
  const visStart = start + ((view.left - COL) / PPM) * 60000 - 3600_000;
  const visEnd = start + ((view.left + view.w) / PPM) * 60000 + 3600_000;

  const slots = [];
  for (let t = start; t < end; t += SLOT * 60000) {
    const lx = x(t);
    if (lx < view.left - 400 || lx > view.left + view.w + 400) continue;
    const d = new Date(t);
    slots.push(
      <div key={t} className="g-slot" style={{ left: COL + lx, width: SLOT * PPM }}>
        {d.getHours() === 0 && d.getMinutes() === 0 ? <b>{fmtDay(t)}</b> : null} {fmtTime(t)}
      </div>,
    );
  }

  const nowX = COL + x(clock);
  const hasEpg = Object.keys(epg).length > 0;

  return (
    <div className="guide">
      <div className="guide-toolbar">
        <div className="guide-title">
          <Icon name="guide" />
          <span>TV Guide</span>
          <span className="muted">{rows.length.toLocaleString()} channels</span>
        </div>
        <label className="switch">
          <input type="checkbox" checked={onlyEpg} onChange={(e) => setOnlyEpg(e.target.checked)} />
          <span className="track" />
          Only channels with guide data
        </label>
        <button className="btn" onClick={scrollToNow}>
          <Icon name="now" size={16} /> Jump to now
        </button>
      </div>

      {!hasEpg && (
        <div className="guide-empty">
          {epgProgress.phase === 'loading' ? (
            <>
              <div className="spinner" />
              <p>Downloading the TV guide from epgshare01… this can take a moment the first time.</p>
            </>
          ) : (
            <>
              <Icon name="guide" size={36} />
              <p>No guide data yet. Choose EPG regions in Settings that match your channels.</p>
            </>
          )}
        </div>
      )}

      {hasEpg && (
        <div
          className="guide-scroll"
          ref={el}
          onScroll={(e) => {
            const t = e.currentTarget;
            setView((v) => ({ ...v, top: t.scrollTop, left: t.scrollLeft }));
            setHover(null);
          }}
        >
          <div className="guide-canvas" style={{ width: COL + width, height: HEAD + rows.length * ROW }}>
            <div className="g-head" style={{ width: COL + width }}>
              <div className="g-corner">{fmtDay(clock)}</div>
              {slots}
            </div>

            <div className="g-now" style={{ left: nowX, height: HEAD + rows.length * ROW }}>
              <span>{fmtTime(clock)}</span>
            </div>

            {rows.slice(firstRow, lastRow).map((c, i) => {
              const r = firstRow + i;
              const progs = epg[c.epgKey] || [];
              return (
                <div key={c.id} className={`g-row ${c.id === activeId ? 'active' : ''}`} style={{ top: HEAD + r * ROW, width: COL + width }}>
                  <button className="g-ch" onClick={() => onPlay(c)} title={c.name}>
                    <ChannelLogo src={c.logo} name={c.name} size={36} />
                    <span className="truncate">{c.name}</span>
                  </button>
                  {progs.map((p) => {
                    if (p.stop < visStart || p.start > visEnd || p.stop <= start || p.start >= end) return null;
                    const left = Math.max(0, x(p.start));
                    const w = x(Math.min(p.stop, end)) - left;
                    const live = p.start <= clock && p.stop > clock;
                    const past = p.stop <= clock;
                    return (
                      <button
                        key={p.start}
                        className={`g-prog ${live ? 'live' : ''} ${past ? 'past' : ''}`}
                        style={{
                          left: COL + left,
                          width: Math.max(2, w - 3),
                          // keep the title readable when the show started off-screen
                          paddingLeft: 10 + Math.max(0, Math.min(view.left - left, w - 90)),
                        }}
                        onClick={() => onPlay(c)}
                        onMouseEnter={(e) => {
                          const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
                          setHover({ p, c, x: rect.left, y: rect.bottom });
                        }}
                        onMouseLeave={() => setHover(null)}
                      >
                        <span className="g-prog-title">{p.title}</span>
                        <span className="g-prog-time">
                          {fmtTime(p.start)} – {fmtTime(p.stop)}
                        </span>
                      </button>
                    );
                  })}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {hover && (
        <div className="g-tip" style={{ left: Math.min(hover.x, window.innerWidth - 340), top: Math.min(hover.y + 6, window.innerHeight - 180) }}>
          <div className="eyebrow">
            {hover.c.name} · {fmtTime(hover.p.start)} – {fmtTime(hover.p.stop)} · {fmtDuration(hover.p.stop - hover.p.start)}
          </div>
          <div className="g-tip-title">{hover.p.title}</div>
          {hover.p.episode && <div className="muted small">{hover.p.episode}</div>}
          {hover.p.desc && <p>{hover.p.desc}</p>}
        </div>
      )}
    </div>
  );
}
