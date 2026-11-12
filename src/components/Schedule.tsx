import { useEffect, useRef } from 'react';
import type { Channel, EpgProgress, Programme } from '../lib/types';
import { fmtDay, fmtDuration, fmtTime, nowIndex, progress } from '../lib/time';
import { countryName, flag } from '../lib/countries';

interface Props {
  channel: Channel | null;
  programmes?: Programme[];
  clock: number;
  epgProgress: EpgProgress;
}

export function Schedule({ channel, programmes, clock, epgProgress }: Props) {
  const list = useRef<HTMLDivElement>(null);
  const idx = nowIndex(programmes, clock);
  const current = idx >= 0 ? programmes![idx] : undefined;

  useEffect(() => {
    const el = list.current?.querySelector('.sched-item.now') as HTMLElement | null;
    if (el && list.current) list.current.scrollTop = el.offsetTop - list.current.offsetTop - 8;
  }, [channel, programmes]);

  if (!channel) return null;

  const upcoming = programmes?.filter((p) => p.stop > clock - 3600_000).slice(0, 40) ?? [];
  let lastDay = '';

  return (
    <section className="schedule">
      <div className="now-card">
        {current?.icon && <img className="now-art" src={current.icon} alt="" loading="lazy" referrerPolicy="no-referrer" />}
        <div className="now-info">
          <div className="eyebrow">
            {current ? 'On now' : 'Channel'} · {flag(channel.country)} {countryName(channel.country)}
          </div>
          <h2>{current?.title || channel.name}</h2>
          {current ? (
            <>
              <div className="meta">
                {fmtTime(current.start)} – {fmtTime(current.stop)} · {fmtDuration(current.stop - clock)} left
                {current.episode && <> · {current.episode}</>}
                {current.category && <span className="pill">{current.category}</span>}
              </div>
              <div className="bar">
                <div style={{ width: `${progress(current, clock)}%` }} />
              </div>
              {current.desc && <p className="desc">{current.desc}</p>}
            </>
          ) : (
            <p className="desc muted">
              {epgProgress.phase === 'loading'
                ? 'Loading the TV guide…'
                : 'No guide information available for this channel.'}
            </p>
          )}
          <div className="tags">
            {channel.groups.map((g) => (
              <span className="pill" key={g}>
                {g}
              </span>
            ))}
            {channel.tvgId && <span className="pill ghost">{channel.tvgId}</span>}
          </div>
        </div>
      </div>

      {upcoming.length > 0 && (
        <div className="sched-list" ref={list}>
          {upcoming.map((p) => {
            const day = fmtDay(p.start);
            const header = day !== lastDay ? (lastDay = day) : null;
            const isNow = p === current;
            const past = p.stop <= clock;
            return (
              <div key={p.start}>
                {header && <div className="sched-day">{header}</div>}
                <div className={`sched-item ${isNow ? 'now' : ''} ${past ? 'past' : ''}`}>
                  <span className="sched-time">{fmtTime(p.start)}</span>
                  <div className="sched-body">
                    <div className="sched-title">
                      {p.title}
                      {isNow && <span className="live-dot">Now</span>}
                    </div>
                    {p.desc && <div className="sched-desc">{p.desc}</div>}
                  </div>
                  <span className="sched-dur">{fmtDuration(p.stop - p.start)}</span>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </section>
  );
}
