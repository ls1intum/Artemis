import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const libRoot = resolve(repoRoot, 'packages/tum-aet-ui/src/lib');

function componentFiles(directory) {
    return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) {
            return componentFiles(path);
        }
        return entry.name.endsWith('.component.ts') ? [path] : [];
    });
}

// The root class must be static so the global validity styles can exclude the control.
function hostClassTokens(hostBlock) {
    const staticClasses = hostBlock.match(/(?:^|\n)\s*class:\s*'([^']*)'/)?.[1] ?? '';
    return new Set(staticClasses.split(/\s+/).filter(Boolean));
}

function formControlComponents() {
    return componentFiles(libRoot)
        .map((path) => ({ path, source: readFileSync(path, 'utf8') }))
        .filter(({ source }) => source.includes('NG_VALUE_ACCESSOR') || source.includes('FormValueControl'))
        .map(({ path, source }) => {
            const hostBlock = source.match(/\n {4}host:\s*\{(.*?)\n {4}\}/s)?.[1] ?? '';
            return {
                path: path.slice(repoRoot.length + 1),
                selector: source.match(/selector:\s*'([^']+)'/)?.[1],
                classTokens: hostClassTokens(hostBlock),
            };
        });
}

describe('TUM AET UI form controls carry their own root class', () => {
    const controls = formControlComponents();

    it('finds the package form controls', () => {
        expect(controls.length, 'components providing NG_VALUE_ACCESSOR or implementing FormValueControl').toBeGreaterThan(5);
        expect(controls.every((control) => control.selector?.startsWith('tumaet-ui-'))).toBe(true);
    });

    it('is what the application stylesheet excludes the controls by', () => {
        const globalStyles = readFileSync(resolve(repoRoot, 'src/main/webapp/content/scss/global.scss'), 'utf8');

        expect(globalStyles, 'global.scss no longer excludes TUM AET UI controls from the JHipster validity accent').toContain(":not([class*='tumaet-ui-'])");
    });

    it.each(controls)('$selector puts its own class on its host', ({ selector, classTokens }) => {
        expect([...classTokens], `${selector} static host class tokens`).toContain(selector);
    });
});
