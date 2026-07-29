import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    allowedHosts: ['frp-six.com'],
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
