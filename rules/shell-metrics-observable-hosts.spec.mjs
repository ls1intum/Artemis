import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const webapp = resolve(repoRoot, 'src/main/webapp');

/**
 * `observeShellMetrics()` keeps `--navbar-height` and `--footer-height` in step with the rendered navbar and footer, and
 * the three shells size their content region from those variables. `ResizeObserver` reports nothing for a non-replaced
 * inline element, and an Angular component host is inline unless its stylesheet says otherwise — so an observed host
 * without a box silently stops reporting, and the shells keep a stale height whenever it grows without a navigation
 * (a system notification appearing, breadcrumbs wrapping).
 *
 * The unit tests for the observer cannot catch this: they stub `ResizeObserver` with an implementation that happily
 * invokes callbacks for any target, inline or not. Hence this invariant lives here, checked against the sources.
 */
const walk = (dir, out = []) => {
    for (const entry of readdirSync(dir)) {
        if (entry === 'node_modules') continue;
        const full = join(dir, entry);
        if (statSync(full).isDirectory()) walk(full, out);
        else if (entry.endsWith('.ts')) out.push(full);
    }
    return out;
};

const observedSelectors = () => {
    const util = readFileSync(resolve(webapp, 'app/foundation/util/navbar.util.ts'), 'utf8');
    const block = util.match(/const SHELL_METRICS[\s\S]*?\n\];/);
    expect(block, 'SHELL_METRICS declaration not found in navbar.util.ts').toBeTruthy();
    return [...block[0].matchAll(/selector:\s*'([^']+)'/g)].map((m) => m[1]);
};

/** Locates the component declaring `selector` and returns the contents of its stylesheets. */
const stylesheetsFor = (selector, allTsFiles) => {
    const needle = `selector: '${selector}'`;
    const file = allTsFiles.find((f) => readFileSync(f, 'utf8').includes(needle));
    expect(file, `no component declares ${needle}`).toBeTruthy();
    const source = readFileSync(file, 'utf8');
    const urls = [...source.matchAll(/styleUrls?:\s*(\[[^\]]*\]|'[^']+')/g)]
        .flatMap((m) => [...m[1].matchAll(/'([^']+)'/g)].map((u) => u[1]))
        .map((u) => (u.startsWith('src/') ? resolve(repoRoot, u) : resolve(dirname(file), u)));
    expect(urls.length, `${selector} has no stylesheet to declare a host display in`).toBeGreaterThan(0);
    return urls.map((u) => readFileSync(u, 'utf8'));
};

/** True when a stylesheet gives `:host` a display that produces a box ResizeObserver can report on. */
const declaresBoxedHost = (css) => {
    for (const match of css.matchAll(/:host(?:\([^)]*\))?\s*\{([^}]*)\}/g)) {
        const display = match[1].match(/(?:^|[\s;])display:\s*([a-z-]+)/);
        if (display && display[1] !== 'inline' && display[1] !== 'contents' && display[1] !== 'none') return true;
    }
    return false;
};

describe('shell metric hosts are observable', () => {
    const allTsFiles = walk(resolve(webapp, 'app'));
    const selectors = observedSelectors();

    it('observes at least the navbar and the footer', () => {
        expect(selectors).toContain('jhi-navbar');
        expect(selectors).toContain('jhi-footer');
    });

    it.each(observedSelectors())('%s declares a host display that ResizeObserver can report on', (selector) => {
        const declared = stylesheetsFor(selector, allTsFiles).some(declaresBoxedHost);

        expect(
            declared,
            `${selector} is observed by observeShellMetrics() but its stylesheet leaves the host inline. ` +
                `ResizeObserver does not report size changes for non-replaced inline elements, so the shells would keep ` +
                `a stale height whenever ${selector} grows without a navigation. Add \`:host { display: block; }\`.`,
        ).toBe(true);
    });
});
