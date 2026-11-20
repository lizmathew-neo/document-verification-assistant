import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Relative base so the build works under the GitHub Pages sub-path.
export default defineConfig({
  root: 'demo',
  base: './',
  plugins: [react()],
  build: {
    outDir: '../dist-demo',
    emptyOutDir: true,
  },
});
