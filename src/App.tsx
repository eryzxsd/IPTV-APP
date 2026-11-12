import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Channel, Settings } from './lib/types';
import { DEFAULT_SETTINGS } from './lib/defaults';
import { useEpg, useLocalStorage, useNow, usePlaylists } from './lib/hooks';
import { detectProxy } from './lib/net';
import { nowNext } from './lib/time';
import { countryName, flag } from './lib/countries';
import { Icon } from './components/Icon';
import { Sidebar, type Filter } from './components/Sidebar';
import { VirtualList, type VirtualListHandle } from './components/VirtualList';
import { ChannelRow } from './components/ChannelRow';
import { Player } from './components/Player';
import { Schedule } from './components/Schedule';
import { Guide } from './components/Guide';
import { SettingsModal } from './components/SettingsModal';

type View = 'live' | 'guide';

export default function App() {
  const [settings, setSettings] = useLocalStorage<Settings>('lumen.settings', DEFAULT_SETTINGS);
  const [favorites, setFavorites] = useLocalStorage<string[]>('lumen.favorites', []);
  const [recent, setRecent] = useLocalStorage<string[]>('lumen.recent', []);
  const [lastId, setLastId] = useLocalStorage<string | null>('lumen.last', null);
  const [view, setView] = useLocalStorage<View>('lumen.view', 'live');
  const [filter, setFilter] = useState<Filter>({ kind: 'all' });
  const [query, setQuery] = useState('');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [proxyAvailable, setProxyAvailable] = useState<boolean | null>(null);
  const [current, setCurrent] = useState<Channel | null>(null);
  const listRef = useRef<VirtualListHandle>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const clock = useNow(30_000);

  useEffect(() => {
    detectProxy().then(setProxyAvailable);
  }, []);

  const { channels, loading, error, reload } = usePlaylists(settings, proxyAvailable);
  const { data: epg, progress: epgProgress, reload: reloadEpg } = useEpg(channels, settings, proxyAvailable);

  const byId = useMemo(() => new Map(channels.map((c) => [c.id, c])), [channels]);
  const favSet = useMemo(() => new Set(favorites), [favorites]);

  // Restore the last watched channel once the playlist is in.
  useEffect(() => {
    if (!current && lastId && byId.has(lastId)) setCurrent(byId.get(lastId)!);
  }, [byId, lastId, current]);

  const groups = useMemo(() => {
    const m = new Map<string, number>();
    for (const c of channels) for (const g of c.groups) m.set(g, (m.get(g) || 0) + 1);
    return [...m].sort((a, b) => (a[0] === 'Other' ? 1 : b[0] === 'Other' ? -1 : a[0].localeCompare(b[0])));
  }, [channels]);

  const countries = useMemo(() => {
    const m = new Map<string, number>();
    for (const c of channels) if (c.country) m.set(c.country, (m.get(c.country) || 0) + 1);
    return [...m].sort((a, b) => b[1] - a[1]);
  }, [channels]);

  const filtered = useMemo(() => {
    let list: Channel[];
    switch (filter.kind) {
      case 'favorites':
        list = favorites.map((id) => byId.get(id)).filter(Boolean) as Channel[];
        break;
      case 'recent':
        list = recent.map((id) => byId.get(id)).filter(Boolean) as Channel[];
        break;
      case 'group':
        list = channels.filter((c) => c.groups.includes(filter.value));
        break;
      case 'country':
        list = channels.filter((c) => c.country === filter.value);
        break;
      default:
        list = channels;
    }
    const q = query.trim().toLowerCase();
    if (q) {
      list = list.filter((c) => {
        if (c.name.toLowerCase().includes(q) || c.groups.some((g) => g.toLowerCase().includes(q))) return true;
        const { now } = nowNext(epg[c.epgKey], clock);
        return !!now && now.title.toLowerCase().includes(q);
      });
    }
    return list;
  }, [filter, favorites, recent, channels, byId, query, epg, clock]);

  const play = useCallback(
    (c: Channel) => {
      setCurrent(c);
      setLastId(c.id);
      setRecent((r) => [c.id, ...r.filter((x) => x !== c.id)].slice(0, 30));
    },
    [setLastId, setRecent],
  );

  const toggleFav = useCallback(
    (c: Channel) => setFavorites((f) => (f.includes(c.id) ? f.filter((x) => x !== c.id) : [...f, c.id])),
    [setFavorites],
  );

  const zap = useCallback(
    (dir: 1 | -1) => {
      const list = filtered.length ? filtered : channels;
      if (!list.length) return;
      const i = current ? list.findIndex((c) => c.id === current.id) : -1;
      const n = list[(i + dir + list.length) % list.length];
      play(n);
      listRef.current?.scrollToIndex(list.indexOf(n));
    },
    [filtered, channels, current, play],
  );
  const prev = useCallback(() => zap(-1), [zap]);
  const next = useCallback(() => zap(1), [zap]);

  // "/" focuses search.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === '/' && document.activeElement?.tagName !== 'INPUT' && document.activeElement?.tagName !== 'TEXTAREA') {
        e.preventDefault();
        searchRef.current?.focus();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const filterTitle =
    filter.kind === 'all'
      ? 'All channels'
      : filter.kind === 'favorites'
        ? 'Favorites'
        : filter.kind === 'recent'
          ? 'Recently watched'
          : filter.kind === 'country'
            ? `${flag(filter.value)} ${countryName(filter.value)}`
            : filter.value;

  const currentNN = current ? nowNext(epg[current.epgKey], clock) : {};
  const guideCount = useMemo(() => channels.filter((c) => epg[c.epgKey]?.length).length, [channels, epg]);

  return (
    <div className={`app view-${view}`}>
      <header className="topbar">
        <button className="icon-btn menu-btn" onClick={() => setSidebarOpen(true)} aria-label="Open menu">
          <Icon name="menu" />
        </button>
        <div className="brand">
          <div className="brand-mark">
            <Icon name="play" fill size={14} />
          </div>
          <span>
            Lumen<b>TV</b>
          </span>
        </div>

        <nav className="tabs">
          <button className={view === 'live' ? 'on' : ''} onClick={() => setView('live')}>
            <Icon name="tv" size={16} /> <span>Live</span>
          </button>
          <button className={view === 'guide' ? 'on' : ''} onClick={() => setView('guide')}>
            <Icon name="guide" size={16} /> <span>Guide</span>
          </button>
        </nav>

        <div className="search">
          <Icon name="search" size={16} />
          <input
            ref={searchRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search channels, categories or shows"
            aria-label="Search"
          />
          {query ? (
            <button className="icon-btn small" onClick={() => setQuery('')} aria-label="Clear search">
              <Icon name="x" size={14} />
            </button>
          ) : (
            <kbd>/</kbd>
          )}
        </div>

        <EpgStatus progress={epgProgress} guideCount={guideCount} enabled={settings.epgEnabled} onReload={reloadEpg} />

        <button className="icon-btn" onClick={() => setSettingsOpen(true)} title="Settings" aria-label="Settings">
          <Icon name="settings" />
        </button>
      </header>

      <div className="layout">
        <Sidebar
          filter={filter}
          onFilter={setFilter}
          total={channels.length}
          favCount={favorites.filter((id) => byId.has(id)).length}
          recentCount={recent.filter((id) => byId.has(id)).length}
          groups={groups}
          countries={countries}
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
        />

        {view === 'live' ? (
          <>
            <section className="channels">
              <div className="channels-head">
                <div>
                  <h1 className="truncate">{filterTitle}</h1>
                  <span className="muted small">
                    {loading ? 'Loading…' : `${filtered.length.toLocaleString()} channel${filtered.length === 1 ? '' : 's'}`}
                  </span>
                </div>
                <button className="icon-btn" onClick={reload} title="Reload playlist">
                  <Icon name="refresh" size={16} />
                </button>
              </div>
              {loading ? (
                <div className="skeletons">
                  {Array.from({ length: 10 }, (_, i) => (
                    <div className="skeleton-row" key={i} style={{ animationDelay: `${i * 60}ms` }}>
                      <div className="sk-logo" />
                      <div className="sk-lines">
                        <div />
                        <div />
                      </div>
                    </div>
                  ))}
                </div>
              ) : filtered.length ? (
                <VirtualList
                  ref={listRef}
                  count={filtered.length}
                  rowHeight={72}
                  render={(i) => {
                    const c = filtered[i];
                    return (
                      <ChannelRow
                        channel={c}
                        number={i + 1}
                        now={nowNext(epg[c.epgKey], clock).now}
                        clock={clock}
                        active={current?.id === c.id}
                        fav={favSet.has(c.id)}
                        onPlay={play}
                        onFav={toggleFav}
                      />
                    );
                  }}
                />
              ) : (
                <div className="list-empty">
                  <Icon name={filter.kind === 'favorites' ? 'star' : error ? 'alert' : 'search'} size={28} />
                  <p>
                    {error && !channels.length
                      ? 'Could not load the playlist.'
                      : filter.kind === 'favorites'
                        ? 'No favorites yet — tap the ★ on any channel.'
                        : filter.kind === 'recent'
                          ? 'Channels you watch will show up here.'
                          : 'No channels match your search.'}
                  </p>
                  {error && !channels.length && (
                    <>
                      <pre className="err">{error}</pre>
                      <button className="btn" onClick={reload}>
                        <Icon name="refresh" size={16} /> Try again
                      </button>
                    </>
                  )}
                </div>
              )}
            </section>

            <main className="stage">
              <Player
                channel={current}
                now={currentNN.now}
                next={currentNN.next}
                clock={clock}
                proxyMode={settings.proxyMode}
                proxyAvailable={!!proxyAvailable}
                isFav={!!current && favSet.has(current.id)}
                onToggleFav={() => current && toggleFav(current)}
                onPrev={prev}
                onNext={next}
              />
              <Schedule channel={current} programmes={current ? epg[current.epgKey] : undefined} clock={clock} epgProgress={epgProgress} />
            </main>
          </>
        ) : (
          <main className="stage guide-stage">
            <Guide
              channels={filtered}
              epg={epg}
              epgProgress={epgProgress}
              clock={clock}
              activeId={current?.id ?? null}
              onPlay={(c) => {
                play(c);
                setView('live');
              }}
            />
          </main>
        )}
      </div>

      {settingsOpen && (
        <SettingsModal
          settings={settings}
          proxyAvailable={!!proxyAvailable}
          onClose={() => setSettingsOpen(false)}
          onSave={(s) => {
            setSettings(s);
            setSettingsOpen(false);
          }}
        />
      )}
    </div>
  );
}

function EpgStatus({
  progress,
  guideCount,
  enabled,
  onReload,
}: {
  progress: ReturnType<typeof useEpg>['progress'];
  guideCount: number;
  enabled: boolean;
  onReload: () => void;
}) {
  if (!enabled) return null;
  if (progress.phase === 'loading') {
    const mb = ((progress.bytes || 0) / 1048576).toFixed(1);
    return (
      <div className="epg-status loading" title={progress.source}>
        <div className="spinner tiny" />
        <span>
          Guide {(progress.sourceIndex ?? 0) + 1}/{progress.sourceCount} · {mb} MB
        </span>
      </div>
    );
  }
  if (progress.phase === 'error' || (progress.phase === 'done' && progress.errors?.length)) {
    return (
      <button className="epg-status warn" title={progress.errors?.join('\n')} onClick={onReload}>
        <Icon name="alert" size={14} />
        <span>{guideCount ? `Guide: ${guideCount.toLocaleString()} ch` : 'Guide failed'}</span>
      </button>
    );
  }
  if (progress.phase === 'done') {
    return (
      <button className="epg-status ok" title="Refresh TV guide" onClick={onReload}>
        <span className="dot" />
        <span>Guide: {guideCount.toLocaleString()} ch</span>
      </button>
    );
  }
  return null;
}
