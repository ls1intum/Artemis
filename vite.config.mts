/// <reference types="vitest" />

import angular from '@analogjs/vite-plugin-angular';
import { defineConfig } from 'vite';
import tsconfigPaths from 'vite-tsconfig-paths';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
    return {
        plugins: [angular(), tsconfigPaths()],
        test: {
            globals: true,
            environment: 'jsdom',
            setupFiles: ['src/test/javascript/vitest/vitest-setup.ts'],
            include: ['src/test/javascript/vitest/**/*.spec.ts'],
            exclude: [
                'src/main/resources/**',
                'build/resources/**', // Exclude problematic directories
            ],
            reporters: ['default']
        },
        resolve: {
            alias: {
                // Manually resolve the "app/*" alias if needed
                app: '/src/main/webapp/app',
            },
        },
        build: {
            rollupOptions: {
                external: [/build\/resources\/.*/, /src\/main\/resources\/.*/],
            },
        },
        define: {
            'import.meta.vitest': mode !== 'production',
        },
    };
});
