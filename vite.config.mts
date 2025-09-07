/// <reference types="vitest" />

import angular from '@analogjs/vite-plugin-angular';
import { defineConfig } from 'vite';
import tsconfigPaths from 'vite-tsconfig-paths';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
    return {
        plugins: [angular(), tsconfigPaths()],
        optimizeDeps: {
            exclude: [
                // Large libraries that often cause optimization issues
                'monaco-editor',
                'pdfjs-dist',
                
                // Libraries with complex module structures
                '@ls1intum/apollon',
                '@swimlane/ngx-charts',
                '@swimlane/ngx-graph',
                
                // CommonJS packages that don't play well with ESM optimization
                'jszip',
                'xlsx',
                'interactjs',
                'crypto-js',
                'dompurify',
                'papaparse',
                'turndown',
                'emoji-js',
                'export-to-csv',
                'mobile-drag-drop',
                'webstomp-client',
                'js-video-url-parser',
                'markdown-it-highlightjs',
                '@vscode/markdown-it-katex',
                'pdf-lib',
                'pako',
                'fast-json-patch',
                'diff-match-patch-typescript',
                'html-diff-ts',
                'franc-min',
                'graphemer',
                'simple-statistics',
                'smoothscroll-polyfill',
                'split.js',
                'ts-cacheable',
                'ismobilejs-es5',
                
                // Angular and related packages that should not be optimized
                '@angular/core',
                '@angular/common',
                '@angular/forms',
                '@angular/router',
                '@angular/platform-browser',
                '@angular/platform-browser-dynamic',
                '@angular/animations',
                '@angular/cdk',
                '@angular/material',
                '@ng-bootstrap/ng-bootstrap',
                'primeng',
                
                // Other potentially problematic dependencies
                '@fortawesome/angular-fontawesome',
                '@fortawesome/fontawesome-svg-core',
                '@fortawesome/free-solid-svg-icons',
                '@fortawesome/free-regular-svg-icons',
                'dayjs',
                'rxjs',
                'zone.js',
                'core-js',
                'bootstrap',
                'lodash-es',
                'uuid',
                'compare-versions',
                'markdown-it',
                'markdown-it-github-alerts',
                'tslib',
            ],
        },
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
