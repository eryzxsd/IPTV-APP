import { useMemo, useState } from 'react';
import { Icon } from './Icon';
import { countryName, flag } from '../lib/countries';

export type Filter =
  | { kind: 'all' }
  | { kind: 'favorites' }
  | { kind: 'recent' }
  | { kind: 'group'; value: string }
  | { kind: 'country'; value: string };

export const sameFilter = (a: Filter, b: Filter) =>
  a.kind === b.kind && ('value' in a ? a.value : '') === ('value' in b ? b.value : '');

interface Props {
  filter: Filter;
  onFilter: (f: Filter) => void;
  total: number;
  favCount: number;
  recentCount: number;
  groups: [string, number][];
  countries: [string, number][];
  open: boolean;
  onClose: () => void;
}

export function Sidebar({ filter, onFilter, total, favCount, recentCount, groups, countries, open, onClose }: Props) {
  const [showAllCountries, setShowAllCountries] = useState(false);
  const [countryQuery, setCountryQuery] = useState('');
  const pick = (f: Filter) => {
    onFilter(f);
    onClose();
  };

  const visibleCountries = useMemo(() => {
    const q = countryQuery.trim().toLowerCase();
    const list = q ? countries.filter(([c]) => countryName(c).toLowerCase().includes(q) || c.toLowerCase() === q) : countries;
    return showAllCountries || q ? list : list.slice(0, 12);
  }, [countries, countryQuery, showAllCountries]);

  const Item = ({ f, icon, label, count, emoji }: { f: Filter; icon?: string; label: string; count: number; emoji?: string }) => (
    <button className={`nav-item ${sameFilter(filter, f) ? 'active' : ''}`} onClick={() => pick(f)}>
      {emoji ? <span className="nav-emoji">{emoji}</span> : <Icon name={icon || 'tag'} size={16} />}
      <span className="truncate">{label}</span>
      <span className="count">{count.toLocaleString()}</span>
    </button>
  );

  return (
    <>
      <div className={`scrim ${open ? 'show' : ''}`} onClick={onClose} />
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="nav-section">
          <Item f={{ kind: 'all' }} icon="grid" label="All channels" count={total} />
          <Item f={{ kind: 'favorites' }} icon="star" label="Favorites" count={favCount} />
          <Item f={{ kind: 'recent' }} icon="clock" label="Recently watched" count={recentCount} />
        </div>

        {groups.length > 0 && (
          <div className="nav-section">
            <div className="nav-heading">Categories</div>
            {groups.map(([g, n]) => (
              <Item key={g} f={{ kind: 'group', value: g }} icon={groupIcon(g)} label={g} count={n} />
            ))}
          </div>
        )}

        {countries.length > 0 && (
          <div className="nav-section">
            <div className="nav-heading">Countries</div>
            <div className="nav-search">
              <Icon name="search" size={14} />
              <input value={countryQuery} onChange={(e) => setCountryQuery(e.target.value)} placeholder="Find a country" />
            </div>
            {visibleCountries.map(([c, n]) => (
              <Item key={c} f={{ kind: 'country', value: c }} emoji={flag(c)} label={countryName(c)} count={n} />
            ))}
            {!countryQuery && countries.length > 12 && (
              <button className="nav-more" onClick={() => setShowAllCountries((s) => !s)}>
                <Icon name={showAllCountries ? 'chevronUp' : 'chevronDown'} size={14} />
                {showAllCountries ? 'Show less' : `Show all ${countries.length}`}
              </button>
            )}
          </div>
        )}
      </aside>
    </>
  );
}

function groupIcon(g: string): string {
  const s = g.toLowerCase();
  if (s.includes('news')) return 'info';
  if (s.includes('movie') || s.includes('series')) return 'tv';
  if (s.includes('music')) return 'volume';
  return 'tag';
}
