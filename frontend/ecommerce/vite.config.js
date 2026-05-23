import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Proxy backend calls during dev so the browser stays on a single
    // origin (avoids CORS preflights). Backend serves under /arusuvai
    // on http://localhost:8080.
    proxy: {
      '/arusuvai': 'http://localhost:8080',
    },
  },
})
