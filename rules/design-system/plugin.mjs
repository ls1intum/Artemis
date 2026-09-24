import { noArbitraryValues } from './rules/no-arbitrary-values.mjs';
import { noInlineStyles } from './rules/no-inline-styles.mjs';
import { noRawColors } from './rules/no-raw-colors.mjs';
import { noRestyle } from './rules/no-restyle.mjs';
import { noUnknownClasses } from './rules/no-unknown-classes.mjs';
import { requireStaticClasses } from './rules/require-static-classes.mjs';
export const rules = {
    'no-restyle': noRestyle,
    'no-raw-colors': noRawColors,
    'no-arbitrary-values': noArbitraryValues,
    'no-inline-styles': noInlineStyles,
    'require-static-classes': requireStaticClasses,
    'no-unknown-classes': noUnknownClasses,
};
export const plugin = {
    meta: { name: 'design-system' },
    rules,
};
