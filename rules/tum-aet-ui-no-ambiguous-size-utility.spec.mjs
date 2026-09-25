import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const appRoot = resolve(repoRoot, 'src/main/webapp/app');

// Bootstrap gives these percentage sizing classes !important priority. Charts need explicit dimensions.
const AMBIGUOUS = /\b[hw]-(25|50|75|100)\b/;

function htmlFiles(directory) {
    return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) {
            return htmlFiles(path);
        }
        return entry.name.endsWith('.html') ? [path] : [];
    });
}

function tumAetUiTags() {
    return htmlFiles(appRoot).flatMap((path) => {
        const source = readFileSync(path, 'utf8');
        return [...source.matchAll(/<(tumaet-ui-[a-z-]*chart[a-z-]*)\b([^>]*)>/g)].map((match) => ({
            file: path.slice(repoRoot.length + 1),
            element: match[1],
            attributes: match[2],
        }));
    });
}

describe('TUM AET UI charts are not sized with a class Bootstrap also defines', () => {
    const offenders = tumAetUiTags()
        .filter(({ attributes }) => {
            const classes = attributes.match(/\sclass="([^"]*)"/)?.[1] ?? '';
            return AMBIGUOUS.test(classes);
        })
        .map(({ file, element, attributes }) => `${file}: <${element} class="${attributes.match(/\sclass="([^"]*)"/)[1]}">`);

    it('finds TUM AET UI charts to check', () => {
        expect(tumAetUiTags().length, 'TUM AET UI chart elements in application templates').toBeGreaterThan(10);
    });

    it('sizes every TUM AET UI chart unambiguously', () => {
        expect(offenders, 'use an explicit size such as h-[200px] instead, which Bootstrap does not define').toEqual([]);
    });
});
