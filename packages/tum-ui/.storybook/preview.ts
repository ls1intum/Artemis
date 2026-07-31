import addonA11y from '@storybook/addon-a11y';
import addonDocs from '@storybook/addon-docs';
import { setCompodocJson } from '@storybook/addon-docs/angular';
import addonThemes, { withThemeByDataAttribute } from '@storybook/addon-themes';
import addonVitest from '@storybook/addon-vitest';
import { definePreview } from '@storybook/angular-vite';

import '../styles.css';
import '../themes.css';
import documentationJson from '../documentation.json';
import { ThemedDocsContainer } from './docs-container';
import { preferredTheme } from './theme';
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
            defaultTheme: preferredTheme(),
            attributeName: 'data-theme',
            parentSelector: 'html',
        }),
    ],
    parameters: {
        a11y: {
            options: {
                runOnly: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'],
            },
            test: 'error',
        },
        docs: {
            container: ThemedDocsContainer,
            controls: {
                sort: 'requiredFirst',
            },
            toc: true,
        },
        controls: {
            expanded: true,
            sort: 'requiredFirst',
        },
        layout: 'centered',
    },
    tags: ['autodocs'],
});
