import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev mode: run the Spring Boot backend on 8080, then `npm run dev` here and
// open http://localhost:5173 — /api is proxied to the backend.
// Build mode: `npm run build` outputs straight into the backend's static
// resources, so `mvn spring-boot:run` serves the app on http://localhost:8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  }
})
