import { fileURLToPath, URL } from 'node:url';
import { createDesignSystemStyleRule } from './angular-design-system-stylelint.mjs';
import { inlineStyleOptions } from './tum-ui-design-system.mjs';

export default createDesignSystemStyleRule({
    components: [fileURLToPath(new URL('../packages/tum-ui/src', import.meta.url))],
    propertyOptions: inlineStyleOptions,
});
