import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // amazon-cognito-identity-js referencia `global` (Node), inexistente no browser
  define: {
    global: 'globalThis',
  },
  server: {
    proxy: {
      // floci (emulador Cognito local) não responde preflight CORS — proxy deixa
      // a chamada same-origin, sem precisar de CORS no floci
      '/cognito-local': {
        target: 'http://localhost:4566',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/cognito-local/, ''),
      },
    },
  },
})
