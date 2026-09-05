import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const libRoot = resolve(repoRoot, 'packages/tum-ui/src/lib');

function componentFiles(directory) {
    return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) {
            return componentFiles(path);
        }
        return entry.name.endsWith('.component.ts') ? [path] : [];
    });
}

/** Class tokens the component puts on its own host, whether as a static `class:` or through a `[class]` binding. */
function hostClassTokens(source, hostBlock) {
    const hasClassBinding = /'\[class\]'\s*:/.test(hostBlock);
    const staticClasses = hostBlock.match(/(?:^|\n)\s*class:\s*'([^']*)'/)?.[1] ?? '';
    // A `[class]` binding is computed, so take every class-shaped literal in the file as a candidate token.
    const computedClasses = hasClassBinding ? [...source.matchAll(/[`'"]([^`'"]*\btum-ui-[\w-]+\b[^`'"]*)[`'"]/g)].map((match) => match[1]) : [];
    return new Set([staticClasses, ...computedClasses].flatMap((classes) => classes.split(/\s+/)).filter(Boolean));
}

/** Components Angular stamps `ng-valid` / `ng-invalid` / `ng-dirty` onto, because they are the form control. */
function formControlComponents() {
    return componentFiles(libRoot)
        .map((path) => ({ path, source: readFileSync(path, 'utf8') }))
        .filter(({ source }) => source.includes('NG_VALUE_ACCESSOR') || source.includes('FormValueControl'))
        .map(({ path, source }) => {
            const hostBlock = source.match(/\n {4}host:\s*\{(.*?)\n {4}\}/s)?.[1] ?? '';
            return {
                path: path.slice(repoRoot.length + 1),
                selector: source.match(/selector:\s*'([^']+)'/)?.[1],
                classTokens: hostClassTokens(source, hostBlock),
            };
        });
}

describe('TUM UI form controls carry their own root class', () => {
    const controls = formControlComponents();

    it('finds the package form controls', () => {
        expect(controls.length, 'components providing NG_VALUE_ACCESSOR or implementing FormValueControl').toBeGreaterThan(5);
        expect(controls.every((control) => control.selector?.startsWith('tum-ui-'))).toBe(true);
    });

    it('is what the application stylesheet excludes the controls by', () => {
        const globalStyles = readFileSync(resolve(repoRoot, 'src/main/webapp/content/scss/global.scss'), 'utf8');

        // Without this rule the guard below would be protecting nothing.
        expect(globalStyles, 'global.scss no longer excludes TUM UI controls from the JHipster validity accent').toContain(":not([class*='tum-ui-'])");
    });

    it.each(controls)('$selector puts its own class on its host', ({ selector, classTokens }) => {
        // A control whose host carries no `tum-ui-` class is not excluded from the JHipster validity accent, so
        // it grows a green or red 5px bar the moment a form marks it required-and-valid, or dirty and invalid.
        expect([...classTokens], `${selector} host class tokens`).toContain(selector);
    });
});
