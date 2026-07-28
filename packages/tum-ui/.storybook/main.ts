import { defineMain } from '@storybook/angular-vite/node';

export default defineMain({
    stories: ['../src/**/*.stories.ts'],
    addons: ['@storybook/addon-a11y', '@storybook/addon-docs', '@storybook/addon-themes', '@storybook/addon-vitest'],
    framework: {
        name: '@storybook/angular-vite',
        options: {
            compodoc: false,
        },
    },
    core: {
        disableTelemetry: true,
    },
    features: {
        angularFilterNonInputControls: true,
        backgrounds: false,
    },
});
