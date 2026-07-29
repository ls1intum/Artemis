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
        coverage: {
            provider: 'istanbul',
            include: ['src/lib/**/*.ts'],
            exclude: ['src/**/*.spec.ts', 'src/**/*.stories.ts'],
            reporter: ['text', 'json-summary'],
            thresholds: {
                lines: 90,
                statements: 90,
                functions: 90,
                branches: 75,
            },
        },
    },
});
