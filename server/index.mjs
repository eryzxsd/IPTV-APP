// Production server: serves the built app from ./dist and mounts the proxy.
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createAppServer, listen } from './app.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const port = Number(process.env.PORT) || 8080;

if (!existsSync(root)) {
  console.error('dist/ not found — run `npm run build` first.');
  process.exit(1);
}

listen(createAppServer(root), port).then((p) => console.log(`Lumen TV running on http://localhost:${p}`));
