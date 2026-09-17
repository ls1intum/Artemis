import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const appRoot = resolve(repoRoot, 'src/main/webapp/app');

/**
 * Bootstrap ships `.h-50 { height: 50% !important }` and the same for 25/75/100 on both axes. Tailwind reads
 * those very class names as its spacing scale — `h-50` is 12.5rem — so the two disagree and Bootstrap's
 * `!important` wins.
 *
 * Scoped to charts on purpose. Elsewhere the collision is harmless in practice: `w-100` on a button means the
 * Bootstrap 100% the author wanted, and Bootstrap winning is the intended outcome. A chart is different — it
 * derives its geometry from the box it is given, so a box that silently collapses to a percentage of an
 * unrelated parent renders a ring a fraction of its intended size, which is how the exercise statistics
 * doughnut ended up a 35px ring underneath its own label.
 */
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

/** Opening tags of TUM UI elements, with their attributes, across every application template. */
function tumUiTags() {
    return htmlFiles(appRoot).flatMap((path) => {
        const source = readFileSync(path, 'utf8');
        return [...source.matchAll(/<(tum-ui-[a-z-]*chart[a-z-]*)\b([^>]*)>/g)].map((match) => ({
            file: path.slice(repoRoot.length + 1),
            element: match[1],
            attributes: match[2],
        }));
    });
}

describe('TUM UI charts are not sized with a class Bootstrap also defines', () => {
    const offenders = tumUiTags()
        .filter(({ attributes }) => {
            const classes = attributes.match(/\sclass="([^"]*)"/)?.[1] ?? '';
            return AMBIGUOUS.test(classes);
        })
        .map(({ file, element, attributes }) => `${file}: <${element} class="${attributes.match(/\sclass="([^"]*)"/)[1]}">`);

    it('finds TUM UI charts to check', () => {
        expect(tumUiTags().length, 'TUM UI chart elements in application templates').toBeGreaterThan(10);
    });

    it('sizes every TUM UI chart unambiguously', () => {
        expect(offenders, 'use an explicit size such as h-[200px] instead, which Bootstrap does not define').toEqual([]);
    });
});
