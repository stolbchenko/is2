import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: '/is1/',
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/is1/api': 'http://localhost:8080',
      '/is1/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
