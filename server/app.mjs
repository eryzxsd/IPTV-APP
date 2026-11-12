// HTTP server that serves the built app and mounts the CORS proxy.
// Used by `npm start` (server/index.mjs) and by the desktop app (desktop/main.mjs).
import http from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { extname, join, normalize } from 'node:path';
import { createProxyHandler } from './proxy.mjs';

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.json': 'application/json',
  '.webmanifest': 'application/manifest+json',
};

export function createAppServer(root) {
  const proxy = createProxyHandler();
  return http.createServer((req, res) => {
    proxy(req, res, () => {
      const path = normalize(decodeURIComponent((req.url || '/').split('?')[0])).replace(/^(\.\.[/\\])+/, '');
      let file = join(root, path);
      if (!file.startsWith(root) || !existsSync(file) || statSync(file).isDirectory()) {
        file = join(root, 'index.html');
      }
      res.setHeader('Content-Type', TYPES[extname(file)] || 'application/octet-stream');
      if (file.startsWith(join(root, 'assets'))) {
        res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
      }
      createReadStream(file).pipe(res);
    });
  });
}

/** Starts the server, resolving to the port it listens on. */
export function listen(server, port, host) {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, host, () => {
      server.off('error', reject);
      resolve(server.address().port);
    });
  });
}
