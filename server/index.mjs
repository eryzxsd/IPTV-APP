// Production server: serves the built app from ./dist and mounts the proxy.
import http from 'node:http';
import { createReadStream, existsSync, statSync } from 'node:fs';
import { extname, join, normalize, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createProxyHandler } from './proxy.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const port = Number(process.env.PORT) || 8080;
const proxy = createProxyHandler();

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

if (!existsSync(root)) {
  console.error('dist/ not found — run `npm run build` first.');
  process.exit(1);
}

http
  .createServer((req, res) => {
    proxy(req, res, () => {
      const path = normalize(decodeURIComponent((req.url || '/').split('?')[0])).replace(/^(\.\.[/\\])+/, '');
      let file = join(root, path);
      if (!file.startsWith(root) || !existsSync(file) || statSync(file).isDirectory()) {
        file = join(root, 'index.html');
      }
      const ext = extname(file);
      res.setHeader('Content-Type', TYPES[ext] || 'application/octet-stream');
      if (file.includes(`${join(root, 'assets')}`)) {
        res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
      }
      createReadStream(file).pipe(res);
    });
  })
  .listen(port, () => console.log(`Lumen TV running on http://localhost:${port}`));
