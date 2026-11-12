import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
// @ts-expect-error plain ESM module without types
import { createProxyHandler } from './server/proxy.mjs';

// The built-in CORS proxy is mounted in dev too, so playlists, EPG files
// and http:// streams work exactly like they do with `npm start`.
export default defineConfig({
  plugins: [
    react(),
    {
      name: 'iptv-proxy',
      configureServer(server) {
        server.middlewares.use(createProxyHandler());
      },
      configurePreviewServer(server) {
        server.middlewares.use(createProxyHandler());
      },
    },
  ],
  worker: { format: 'es' },
  build: { chunkSizeWarningLimit: 1200 },
});
