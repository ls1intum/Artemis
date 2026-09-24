import { defineMain } from '@storybook/angular-vite/node';

export default defineMain({
    stories: ['../src/**/*.mdx', '../src/**/*.stories.ts'],
    addons: ['@storybook/addon-a11y', '@storybook/addon-docs', '@storybook/addon-themes', '@storybook/addon-vitest'],
    framework: {
        name: '@storybook/angular-vite',
        options: {
            propsTable: 'api',
        },
    },
    core: {
        disableTelemetry: true,
    },
    features: {
        backgrounds: false,
    },
});
