import addonA11y from '@storybook/addon-a11y';
import addonDocs from '@storybook/addon-docs';
import { setCompodocJson } from '@storybook/addon-docs/angular';
import addonThemes, { withThemeByDataAttribute } from '@storybook/addon-themes';
import addonVitest from '@storybook/addon-vitest';
import { definePreview } from '@storybook/angular-vite';

import '../../../dist/tum-ui/styles.css';
import documentationJson from '../documentation.json';
import { ThemedDocsContainer } from './docs-container';
import './theme.css';

setCompodocJson(documentationJson);

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
        docs: {
            container: ThemedDocsContainer,
        },
        layout: 'centered',
    },
    tags: ['autodocs'],
});
