import path from 'node:path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    proxy: {
      // Même chemin relatif qu'en prod via l'Ingress (/api/v1/<service>/...),
      // pas de CORS à gérer ni en local ni en prod.
      '/api': 'http://localhost:8080',
    },
  },
})
