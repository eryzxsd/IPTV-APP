// Desktop (Windows/macOS/Linux) shell: runs the same local server as
// `npm start` — static app + CORS proxy — and shows it in a window.
import { app, BrowserWindow, shell, Menu } from 'electron';
import { join } from 'node:path';
import { createAppServer, listen } from '../server/app.mjs';

// A fixed port keeps the page origin stable, so favorites and settings
// (stored per origin) survive restarts. Falls back to a random port.
const PREFERRED_PORT = 47821;

if (!app.requestSingleInstanceLock()) app.quit();

let win = null;

async function startServer() {
  const server = createAppServer(join(app.getAppPath(), 'dist'));
  try {
    return await listen(server, PREFERRED_PORT, '127.0.0.1');
  } catch {
    return listen(server, 0, '127.0.0.1');
  }
}

async function createWindow() {
  const port = await startServer();
  win = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 380,
    minHeight: 500,
    backgroundColor: '#07080c',
    title: 'Lumen TV',
    icon: join(app.getAppPath(), 'build', 'icon.png'),
    autoHideMenuBar: true,
    show: false,
    webPreferences: { contextIsolation: true, sandbox: true },
  });
  Menu.setApplicationMenu(null);
  win.once('ready-to-show', () => win.show());

  // Open external links (e.g. epgshare01 in Settings) in the browser.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) shell.openExternal(url);
    return { action: 'deny' };
  });
  win.webContents.on('before-input-event', (_e, input) => {
    if (input.type === 'keyDown' && input.key === 'F11') win.setFullScreen(!win.isFullScreen());
  });

  await win.loadURL(`http://127.0.0.1:${port}/`);
}

app.on('second-instance', () => {
  if (win) {
    if (win.isMinimized()) win.restore();
    win.focus();
  }
});
app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
