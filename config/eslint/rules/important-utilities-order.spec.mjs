import { describe, it, expect } from 'vitest';
import { compileString } from 'sass';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const scssDirectory = resolve(dirname(fileURLToPath(import.meta.url)), '../../../src/main/webapp/content/scss');

/**
 * The important utilities of content/scss/_utilities.scss all have the same specificity and importance, so between
 * two of them on one element the later rule wins. A class for one side therefore has to come after the class for all
 * sides: `p-1 pb-0` must end without bottom padding, in whatever order the template lists the two.
 */
describe('order of the important spacing utilities', () => {
    // The file reads its colours from the theme that imports it; any values do for a check of the order.
    const themeVariables = '$primary: red; $border-color: red; $danger: red; $info: red; $white: red;';
    const { css } = compileString(`${themeVariables} @import 'utilities';`, { loadPaths: [scssDirectory], silenceDeprecations: ['import'] });
    const position = (className) => {
        // A leading line break keeps `.p-1` from matching inside another selector; it is added for the first rule.
        const index = `\n${css}`.indexOf(`\n.${className} {`);
        expect(index, `.${className} is not defined`).toBeGreaterThanOrEqual(0);
        return index;
    };

    it.each([
        ['p-1', 'pb-0'],
        ['p-2', 'pt-0'],
        ['p-0', 'px-2'],
        ['px-2', 'ps-0'],
        ['py-1', 'pb-0'],
        ['m-0', 'mb-2'],
        ['m-2', 'mt-0'],
        ['mx-auto', 'me-0'],
        ['my-0', 'mt-auto'],
    ])('defines %s before %s', (allSides, oneSide) => {
        expect(position(allSides)).toBeLessThan(position(oneSide));
    });
});
