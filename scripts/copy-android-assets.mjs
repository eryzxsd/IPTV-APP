// Copies the built web app (dist/) into the Android project's assets.
import { cpSync, existsSync, rmSync } from 'node:fs';

const dest = 'android/app/src/main/assets/www';
if (!existsSync('dist/index.html')) {
  console.error('dist/ not found — run `npm run build` first.');
  process.exit(1);
}
rmSync(dest, { recursive: true, force: true });
cpSync('dist', dest, { recursive: true });
console.log(`Copied dist/ -> ${dest}`);
