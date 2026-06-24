  import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev proxy: /api -> Spring Boot (8080). Shu tufayli cookie same-origin bo'ladi (CORS muammosi yo'q).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
