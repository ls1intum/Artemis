import addonA11y from '@storybook/addon-a11y';
import addonDocs from '@storybook/addon-docs';
import addonThemes, { withThemeByDataAttribute } from '@storybook/addon-themes';
import addonVitest from '@storybook/addon-vitest';
import { definePreview } from '@storybook/angular-vite';

import '../../../dist/tum-ui/styles.css';
import './theme.css';

export default definePreview({
    addons: [addonA11y(), addonDocs(), addonThemes(), addonVitest()],
    decorators: [
        withThemeByDataAttribute({
            themes: {
                light: 'light',
                dark: 'dark',
            },
            defaultTheme: 'light',
            attributeName: 'data-theme',
            parentSelector: 'html',
        }),
    ],
    parameters: {
        a11y: {
            test: 'error',
        },
        controls: {
            matchers: {
                color: /(background|color)$/i,
                date: /Date$/i,
            },
        },
        layout: 'centered',
    },
    tags: ['autodocs'],
});
