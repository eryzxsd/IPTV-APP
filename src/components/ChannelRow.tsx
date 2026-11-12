import { memo } from 'react';
import type { Channel, Programme } from '../lib/types';
import { flag } from '../lib/countries';
import { fmtTime, progress } from '../lib/time';
import { ChannelLogo } from './Logo';
import { Icon } from './Icon';

interface Props {
  channel: Channel;
  number: number;
  now?: Programme;
  clock: number;
  active: boolean;
  fav: boolean;
  onPlay: (c: Channel) => void;
  onFav: (c: Channel) => void;
}

export const ChannelRow = memo(function ChannelRow({ channel, number, now, clock, active, fav, onPlay, onFav }: Props) {
  return (
    <div
      className={`channel-row ${active ? 'active' : ''}`}
      onClick={() => onPlay(channel)}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === 'Enter' && onPlay(channel)}
    >
      <span className="ch-num">{number}</span>
      <ChannelLogo src={channel.logo} name={channel.name} />
      <div className="ch-body">
        <div className="ch-name">
          <span className="truncate">{channel.name}</span>
          {channel.country && <span className="ch-flag" title={channel.country}>{flag(channel.country)}</span>}
        </div>
        {now ? (
          <>
            <div className="ch-now truncate">
              <span className="time">{fmtTime(now.start)}</span> {now.title}
            </div>
            <div className="mini-progress">
              <div style={{ width: `${progress(now, clock)}%` }} />
            </div>
          </>
        ) : (
          <div className="ch-now muted truncate">{channel.groups.join(' · ')}</div>
        )}
      </div>
      {active && <span className="eq" aria-hidden><i /><i /><i /></span>}
      <button
        className={`icon-btn small fav ${fav ? 'fav-on' : ''}`}
        onClick={(e) => {
          e.stopPropagation();
          onFav(channel);
        }}
        title={fav ? 'Remove from favorites' : 'Add to favorites'}
      >
        <Icon name="star" fill={fav} size={16} />
      </button>
    </div>
  );
});
