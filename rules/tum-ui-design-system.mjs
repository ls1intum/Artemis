import { URL, fileURLToPath } from 'node:url';
import { createAngularDesignSystemPlugin } from './angular-design-system.mjs';

export const tumUiDesignSystem = createAngularDesignSystemPlugin({
    root: fileURLToPath(new URL('../', import.meta.url)),
    components: ['packages/tum-ui/src'],
    sources: ['src/main/webapp/app'],
    theme: 'src/main/webapp/tailwind.css',
    scope: 'components',
});

export const inlineStyleOptions = {
    allow: [
        'width',
        'height',
        'min-*',
        'max-*',
        'margin',
        'margin-*',
        'display',
        'position',
        'inset',
        'inset-*',
        'top',
        'right',
        'bottom',
        'left',
        'overflow',
        'overflow-*',
        'flex',
        'flex-*',
        'grid',
        'grid-*',
        'gap',
        'row-gap',
        'column-gap',
        'align-*',
        'justify-*',
        'order',
        'z-index',
        'white-space',
        'text-align',
        'table-layout',
        'resize',
        'pointer-events',
        'cursor',
    ],
};

// Explicit policy, not a second class grammar. The native policy engine owns utility classification.
export const tumUiDesignSystemRules = {
    'design-system/no-restyle': ['error', { allow: ['layout'] }],
    'design-system/require-static-classes': 'error',
    'design-system/require-lintable-templates': 'error',
    'design-system/no-unknown-classes': 'error',
    'design-system/no-raw-colors': 'error',
    'design-system/no-literal-inline-colors': 'error',
    'design-system/no-arbitrary-values': ['error', { allow: ['layout'] }],
    'design-system/no-inline-styles': ['error', inlineStyleOptions],
    'design-system/no-restyle-class-selectors': ['error', { propertyOptions: inlineStyleOptions, privateClassPrefix: 'tum-ui-' }],
    'design-system/no-restyle-stylesheets': ['error', { propertyOptions: inlineStyleOptions, privateClassPrefix: 'tum-ui-' }],
};
