import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        globals: true,
        environment: 'node',
        reporters: ['default'],
        // Load the compiled exercise code natively instead of through vite, so that an instance
        // created inside the exercise passes `instanceof` checks in the tests.
        server: { deps: { external: [/\/dist\//] } },
    },
});
