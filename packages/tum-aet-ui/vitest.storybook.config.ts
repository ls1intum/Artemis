import { storybookTest } from '@storybook/addon-vitest/vitest-plugin';
import { storybookAngularVitest } from '@storybook/angular-vite/vitest';
import { playwright } from '@vitest/browser-playwright';
import { defineConfig } from 'vitest/config';

function storybookProject(theme: 'light' | 'dark') {
    return {
        plugins: [storybookAngularVitest({ zoneless: true }), storybookTest({ configDir: '.storybook', initialGlobals: { theme } })],
        test: {
            name: `storybook-${theme}`,
            browser: {
                enabled: true,
                provider: playwright({}),
                headless: true,
                instances: [{ browser: 'chromium' as const }],
            },
        },
    };
}

export default defineConfig({
    test: {
        projects: [storybookProject('light'), storybookProject('dark')],
    },
});
