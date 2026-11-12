import { useState } from 'react';
import type { ProxyMode, Settings } from '../lib/types';
import { DEFAULT_SETTINGS, EPGSHARE_REGIONS, IPTV_ORG_PLAYLISTS, epgshareUrl } from '../lib/defaults';
import { cacheClear } from '../lib/idb';
import { Icon } from './Icon';

interface Props {
  settings: Settings;
  proxyAvailable: boolean;
  onSave: (s: Settings) => void;
  onClose: () => void;
}

const lines = (s: string) =>
  s
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean);

export function SettingsModal({ settings, proxyAvailable, onSave, onClose }: Props) {
  const regionUrls = new Set(EPGSHARE_REGIONS.map((r) => epgshareUrl(r.code)));
  const [playlists, setPlaylists] = useState(settings.playlists.join('\n'));
  const [regions, setRegions] = useState<Set<string>>(
    () => new Set(EPGSHARE_REGIONS.filter((r) => settings.epgUrls.includes(epgshareUrl(r.code))).map((r) => r.code)),
  );
  const [customEpg, setCustomEpg] = useState(settings.epgUrls.filter((u) => !regionUrls.has(u)).join('\n'));
  const [epgEnabled, setEpgEnabled] = useState(settings.epgEnabled);
  const [proxyMode, setProxyMode] = useState<ProxyMode>(settings.proxyMode);

  const toggleRegion = (code: string) =>
    setRegions((r) => {
      const n = new Set(r);
      if (n.has(code)) n.delete(code);
      else n.add(code);
      return n;
    });

  const save = () => {
    const pl = lines(playlists);
    onSave({
      playlists: pl.length ? pl : DEFAULT_SETTINGS.playlists,
      epgUrls: [...EPGSHARE_REGIONS.filter((r) => regions.has(r.code)).map((r) => epgshareUrl(r.code)), ...lines(customEpg)],
      epgEnabled,
      proxyMode,
    });
  };

  const reset = () => {
    setPlaylists(DEFAULT_SETTINGS.playlists.join('\n'));
    setRegions(new Set(EPGSHARE_REGIONS.filter((r) => DEFAULT_SETTINGS.epgUrls.includes(epgshareUrl(r.code))).map((r) => r.code)));
    setCustomEpg('');
    setEpgEnabled(true);
    setProxyMode('auto');
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-label="Settings">
        <header className="modal-head">
          <h2>Settings</h2>
          <button className="icon-btn" onClick={onClose} aria-label="Close">
            <Icon name="x" />
          </button>
        </header>

        <div className="modal-body">
          <section>
            <h3>Playlists</h3>
            <p className="muted small">One M3U URL per line. Default is the iptv-org public playlist.</p>
            <textarea rows={3} value={playlists} onChange={(e) => setPlaylists(e.target.value)} spellCheck={false} />
            <div className="chips">
              {IPTV_ORG_PLAYLISTS.map((p) => (
                <button key={p.url} className={`chip ${lines(playlists).includes(p.url) ? 'on' : ''}`} onClick={() => setPlaylists(p.url)}>
                  {p.label}
                </button>
              ))}
            </div>
          </section>

          <section>
            <div className="row between">
              <h3>TV Guide (EPG)</h3>
              <label className="switch">
                <input type="checkbox" checked={epgEnabled} onChange={(e) => setEpgEnabled(e.target.checked)} />
                <span className="track" />
                Enabled
              </label>
            </div>
            <p className="muted small">
              Guide data from <a href="https://epgshare01.online/" target="_blank" rel="noreferrer">epgshare01</a>. Pick the regions you
              watch — each file is several MB, so fewer regions load faster.
            </p>
            <div className="chips">
              {EPGSHARE_REGIONS.map((r) => (
                <button key={r.code} className={`chip ${regions.has(r.code) ? 'on' : ''}`} onClick={() => toggleRegion(r.code)} disabled={!epgEnabled}>
                  {regions.has(r.code) && <Icon name="check" size={12} />} {r.label}
                </button>
              ))}
            </div>
            <p className="muted small">Additional XMLTV URLs (.xml or .xml.gz), one per line:</p>
            <textarea rows={2} value={customEpg} onChange={(e) => setCustomEpg(e.target.value)} spellCheck={false} disabled={!epgEnabled} placeholder="https://example.com/guide.xml.gz" />
          </section>

          <section>
            <h3>Proxy</h3>
            <p className="muted small">
              Many streams and the EPG server don't allow browser (CORS) access. The bundled proxy fixes that.{' '}
              {proxyAvailable ? <span className="ok">Proxy detected ✓</span> : <span className="warn">Proxy not detected — run the app with `npm start`.</span>}
            </p>
            <div className="segmented">
              {(['auto', 'always', 'never'] as ProxyMode[]).map((m) => (
                <button key={m} className={proxyMode === m ? 'on' : ''} onClick={() => setProxyMode(m)}>
                  {m === 'auto' ? 'Auto (fallback)' : m === 'always' ? 'Always' : 'Never'}
                </button>
              ))}
            </div>
          </section>

          <section>
            <h3>Data</h3>
            <button
              className="btn"
              onClick={async () => {
                await cacheClear();
                location.reload();
              }}
            >
              <Icon name="refresh" size={16} /> Clear cached playlist & guide
            </button>
          </section>
        </div>

        <footer className="modal-foot">
          <button className="btn ghost" onClick={reset}>
            Reset to defaults
          </button>
          <div className="row gap">
            <button className="btn" onClick={onClose}>
              Cancel
            </button>
            <button className="btn primary" onClick={save}>
              Save
            </button>
          </div>
        </footer>
      </div>
    </div>
  );
}
