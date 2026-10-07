import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// base relativa: o mesmo bundle serve /admin/, /mfa/, /auth/admin/, /auth/mfa/
export default defineConfig({
  plugins: [react()],
  base: './',
})
