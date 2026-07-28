import angular from '@analogjs/vite-plugin-angular';
import { defineConfig } from 'vitest/config';

export default defineConfig({
    root: import.meta.dirname,
    css: {
        postcss: { plugins: [] },
    },
    plugins: [angular({ jit: true, fastCompile: true })],
    test: {
        environment: 'jsdom',
        globals: true,
        include: ['src/**/*.spec.ts'],
        setupFiles: ['test-setup.ts'],
    },
});
