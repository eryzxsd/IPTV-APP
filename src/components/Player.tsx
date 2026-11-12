import { useCallback, useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import type { Channel, Programme, ProxyMode } from '../lib/types';
import { preferProxy, proxied } from '../lib/net';
import { fmtTime, progress } from '../lib/time';
import { Icon } from './Icon';
import { ChannelLogo } from './Logo';

interface Props {
  channel: Channel | null;
  now?: Programme;
  next?: Programme;
  clock: number;
  proxyMode: ProxyMode;
  proxyAvailable: boolean;
  isFav: boolean;
  onToggleFav: () => void;
  onPrev: () => void;
  onNext: () => void;
  /** Reports whether a channel's stream works, so the list can flag dead ones. */
  onHealth?: (id: string, ok: boolean) => void;
}

type Status = 'idle' | 'loading' | 'playing' | 'error';

interface Level {
  index: number;
  label: string;
}

export function Player({ channel, now, next, clock, proxyMode, proxyAvailable, isFav, onToggleFav, onPrev, onNext, onHealth }: Props) {
  const wrap = useRef<HTMLDivElement>(null);
  const video = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [status, setStatus] = useState<Status>('idle');
  const [errorMsg, setErrorMsg] = useState('');
  const [paused, setPaused] = useState(false);
  const [muted, setMuted] = useState(() => localStorage.getItem('lumen.muted') === '1');
  const [volume, setVolume] = useState(() => Number(localStorage.getItem('lumen.volume') ?? 0.9));
  const [levels, setLevels] = useState<Level[]>([]);
  const [level, setLevel] = useState(-1);
  const [showLevels, setShowLevels] = useState(false);
  const [controls, setControls] = useState(true);
  const [via, setVia] = useState<'direct' | 'proxy'>('direct');
  const [attempt, setAttempt] = useState(0);
  const hideTimer = useRef<number>(0);
  const healthRef = useRef(onHealth);
  healthRef.current = onHealth;

  // Load the stream whenever the channel changes (or a retry is requested).
  useEffect(() => {
    const v = video.current;
    if (!v || !channel) return;
    const candidates: { url: string; via: 'direct' | 'proxy' }[] = [];
    const canProxy = proxyAvailable && proxyMode !== 'never';
    if (preferProxy(channel.url, proxyMode, proxyAvailable)) candidates.push({ url: proxied(channel.url), via: 'proxy' });
    else {
      candidates.push({ url: channel.url, via: 'direct' });
      if (canProxy) candidates.push({ url: proxied(channel.url), via: 'proxy' });
    }

    let idx = 0;
    let disposed = false;
    let stallTimer = 0;
    setStatus('loading');
    setErrorMsg('');
    setLevels([]);
    setLevel(-1);

    const cleanup = () => {
      clearTimeout(stallTimer);
      hlsRef.current?.destroy();
      hlsRef.current = null;
      v.removeAttribute('src');
      v.load();
    };

    const fail = (msg: string) => {
      if (disposed) return;
      idx++;
      if (idx < candidates.length) start();
      else {
        cleanup();
        setStatus('error');
        setErrorMsg(msg);
        healthRef.current?.(channel.id, false);
      }
    };

    const start = () => {
      cleanup();
      const { url, via } = candidates[idx];
      setVia(via);
      setStatus('loading');
      // Give up on a source that never starts.
      stallTimer = window.setTimeout(() => fail('The stream took too long to respond.'), 20000);
      const isHls = /\.m3u8(\?|$)/i.test(channel.url) || !/\.(mp4|webm|mkv|mp3|aac|ogg)(\?|$)/i.test(channel.url);
      if (isHls && Hls.isSupported()) {
        const hls = new Hls({ enableWorker: true, lowLatencyMode: false, backBufferLength: 30, manifestLoadingMaxRetry: 1 });
        hlsRef.current = hls;
        hls.on(Hls.Events.MANIFEST_PARSED, (_e, data) => {
          const ls = data.levels
            .map((l, index) => ({ index, label: l.height ? `${l.height}p` : l.bitrate ? `${Math.round(l.bitrate / 1000)} kbps` : `Level ${index + 1}` }))
            .sort((a, b) => parseInt(b.label) - parseInt(a.label));
          setLevels(ls.length > 1 ? ls : []);
          v.play().catch(() => setPaused(true));
        });
        hls.on(Hls.Events.ERROR, (_e, data) => {
          if (!data.fatal) return;
          if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
            hls.recoverMediaError();
            return;
          }
          fail(
            data.response?.code
              ? `The stream returned HTTP ${data.response.code}.`
              : 'The stream is offline, geo-blocked, or blocked by the browser.',
          );
        });
        hls.loadSource(url);
        hls.attachMedia(v);
      } else {
        v.src = url;
        v.play().catch(() => setPaused(true));
      }
    };

    const onPlaying = () => {
      clearTimeout(stallTimer);
      setStatus('playing');
      setPaused(false);
      healthRef.current?.(channel.id, true);
    };
    const onError = () => {
      if (!hlsRef.current) fail('This stream format is not supported by your browser.');
    };
    v.addEventListener('playing', onPlaying);
    v.addEventListener('error', onError);
    start();

    return () => {
      disposed = true;
      v.removeEventListener('playing', onPlaying);
      v.removeEventListener('error', onError);
      cleanup();
    };
  }, [channel, proxyMode, proxyAvailable, attempt]);

  useEffect(() => {
    const v = video.current;
    if (!v) return;
    v.muted = muted;
    v.volume = volume;
    localStorage.setItem('lumen.muted', muted ? '1' : '0');
    localStorage.setItem('lumen.volume', String(volume));
  }, [muted, volume]);

  const togglePlay = useCallback(() => {
    const v = video.current;
    if (!v) return;
    if (v.paused) v.play().catch(() => {});
    else v.pause();
  }, []);

  const fullscreen = useCallback(() => {
    const el = wrap.current;
    if (!el) return;
    if (document.fullscreenElement) document.exitFullscreen();
    else if (el.requestFullscreen) el.requestFullscreen();
    else (video.current as HTMLVideoElement & { webkitEnterFullscreen?: () => void })?.webkitEnterFullscreen?.();
  }, []);

  const pip = useCallback(async () => {
    const v = video.current;
    if (!v) return;
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture();
      else await v.requestPictureInPicture();
    } catch {
      /* unsupported */
    }
  }, []);

  // Keyboard shortcuts.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement;
      if (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable) return;
      if (e.key === ' ' || e.key === 'k') {
        e.preventDefault();
        togglePlay();
      } else if (e.key === 'f') fullscreen();
      else if (e.key === 'm') setMuted((m) => !m);
      else if (e.key === 'ArrowUp' || e.key === 'PageUp') {
        e.preventDefault();
        onPrev();
      } else if (e.key === 'ArrowDown' || e.key === 'PageDown') {
        e.preventDefault();
        onNext();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [togglePlay, fullscreen, onPrev, onNext]);

  const poke = () => {
    setControls(true);
    clearTimeout(hideTimer.current);
    hideTimer.current = window.setTimeout(() => setControls(false), 3000);
  };

  const pickLevel = (i: number) => {
    if (hlsRef.current) hlsRef.current.currentLevel = i;
    setLevel(i);
    setShowLevels(false);
  };

  if (!channel) {
    return (
      <div className="player player-empty">
        <div className="empty-hero">
          <div className="empty-icon">
            <Icon name="tv" size={40} />
          </div>
          <h2>Pick a channel to start watching</h2>
          <p>Thousands of free, public channels from iptv-org, with a TV guide from epgshare01.</p>
          <div className="kbd-hints">
            <span><kbd>↑</kbd><kbd>↓</kbd> zap</span>
            <span><kbd>Space</kbd> play/pause</span>
            <span><kbd>F</kbd> fullscreen</span>
            <span><kbd>M</kbd> mute</span>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      ref={wrap}
      className={`player ${controls || paused || status !== 'playing' ? 'show-controls' : ''}`}
      onMouseMove={poke}
      onTouchStart={poke}
      onMouseLeave={() => setControls(false)}
    >
      <video ref={video} playsInline onClick={togglePlay} onDoubleClick={fullscreen} onPause={() => setPaused(true)} onPlay={() => setPaused(false)} />

      {status === 'loading' && (
        <div className="player-overlay">
          <div className="spinner" />
          <span className="overlay-text">Tuning in{via === 'proxy' ? ' via proxy' : ''}…</span>
        </div>
      )}
      {status === 'error' && (
        <div className="player-overlay error">
          <Icon name="alert" size={36} />
          <h3>Can't play this channel</h3>
          <p>
            {errorMsg}
            {channel.geoBlocked && ' This channel is marked as geo-blocked, so it may only work in its home country.'}
          </p>
          <div className="row gap">
            <button className="btn" onClick={() => setAttempt((a) => a + 1)}>
              <Icon name="refresh" size={16} /> Retry
            </button>
            <button className="btn primary" onClick={onNext}>
              Next channel <Icon name="next" size={16} />
            </button>
          </div>
        </div>
      )}

      <div className="player-top">
        <ChannelLogo src={channel.logo} name={channel.name} size={40} />
        <div className="player-title">
          <div className="name">
            <span className="truncate">{channel.name}</span>
            <span className="live-badge">LIVE</span>
            {channel.quality && <span className="q-badge">{channel.quality}</span>}
          </div>
          {now && (
            <div className="sub">
              {fmtTime(now.start)} – {fmtTime(now.stop)} · {now.title}
            </div>
          )}
        </div>
        <button className={`icon-btn ${isFav ? 'fav-on' : ''}`} onClick={onToggleFav} title={isFav ? 'Remove from favorites' : 'Add to favorites'}>
          <Icon name="star" fill={isFav} />
        </button>
      </div>

      <div className="player-bottom">
        {now && (
          <div className="player-progress">
            <div style={{ width: `${progress(now, clock)}%` }} />
          </div>
        )}
        <div className="controls">
          <button className="icon-btn" onClick={onPrev} title="Previous channel (↑)">
            <Icon name="prev" />
          </button>
          <button className="icon-btn big" onClick={togglePlay} title="Play/Pause (Space)">
            <Icon name={paused ? 'play' : 'pause'} fill size={22} />
          </button>
          <button className="icon-btn" onClick={onNext} title="Next channel (↓)">
            <Icon name="next" />
          </button>
          <div className="volume">
            <button className="icon-btn" onClick={() => setMuted((m) => !m)} title="Mute (M)">
              <Icon name={muted || volume === 0 ? 'mute' : 'volume'} />
            </button>
            <input
              type="range"
              min={0}
              max={1}
              step={0.02}
              value={muted ? 0 : volume}
              onChange={(e) => {
                setVolume(Number(e.target.value));
                setMuted(Number(e.target.value) === 0);
              }}
              style={{ ['--val' as string]: `${(muted ? 0 : volume) * 100}%` }}
            />
          </div>
          <div className="spacer" />
          {next && (
            <div className="up-next">
              <span>Next</span> {fmtTime(next.start)} · {next.title}
            </div>
          )}
          {levels.length > 0 && (
            <div className="levels">
              <button className="chip" onClick={() => setShowLevels((s) => !s)}>
                {level === -1 ? 'Auto' : levels.find((l) => l.index === level)?.label}
              </button>
              {showLevels && (
                <div className="menu">
                  <button className={level === -1 ? 'active' : ''} onClick={() => pickLevel(-1)}>
                    Auto
                  </button>
                  {levels.map((l) => (
                    <button key={l.index} className={level === l.index ? 'active' : ''} onClick={() => pickLevel(l.index)}>
                      {l.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
          {'pictureInPictureEnabled' in document && (
            <button className="icon-btn" onClick={pip} title="Picture in picture">
              <Icon name="pip" />
            </button>
          )}
          <button className="icon-btn" onClick={fullscreen} title="Fullscreen (F)">
            <Icon name="fullscreen" />
          </button>
        </div>
      </div>
    </div>
  );
}
