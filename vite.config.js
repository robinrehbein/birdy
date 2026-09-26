import { defineConfig } from 'vite';

// Relative base so the build works from any sub-path (e.g. GitHub Pages).
export default defineConfig({
  base: './',
  build: {
    chunkSizeWarningLimit: 1000, // three.js alone is ~500 kB
  },
});
