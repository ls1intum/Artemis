import { describe, expect, it } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';

type Catalog = { [key: string]: Catalog | string };

/**
 * The landing and the sign-in page start from a slice of the translation catalog (`supporting_scripts/client/i18n-slice.mjs`).
 * A key that the pages show and that the slice lacks is displayed as `translation-not-found[...]` until the full catalog has loaded.
 * This asserts that the keys of the components an anonymous visitor sees on the sign-in page are in the slice, rather than driving
 * the application, and that every entry of the slice names something that exists.
 */

/** The components that an anonymous visitor sees on `/sign-in` besides the pages themselves (the navbar of an anonymous visitor shows keys of `global` only). */
const SHELL_FILES = [
    'app/core/home/home.component.html',
    'app/core/home/home.component.ts',
    'app/core/home/saml2-login/saml2-login.component.html',
    'app/core/layouts/footer/footer.component.html',
    'app/core/layouts/profiles/page-ribbon.component.ts',
    'app/core/alert/alert-overlay.component.html',
    'app/core/theme/theme-switch.component.html',
    'app/shared-ui/connection-warning/connection-warning.component.html',
    'app/core/loading-notification/loading-notification.component.ts',
    'app/account/user/settings/credentials/passkey-settings/webauthn.service.ts',
];

function deepMerge(target: Catalog, source: Catalog): Catalog {
    for (const [key, value] of Object.entries(source)) {
        const existing = target[key];
        target[key] = typeof value === 'object' && typeof existing === 'object' ? deepMerge(existing, value) : value;
    }
    return target;
}

function loadCatalog(language: string): Catalog {
    const directory = `src/main/webapp/i18n/${language}`;
    return readdirSync(directory)
        .filter((file) => file.endsWith('.json'))
        .reduce((catalog, file) => deepMerge(catalog, JSON.parse(readFileSync(`${directory}/${file}`, 'utf8')) as Catalog), {} as Catalog);
}

function valueAt(catalog: Catalog, path: string): Catalog | string | undefined {
    let current: Catalog | string | undefined = catalog;
    for (const segment of path.split('.')) {
        current = typeof current === 'object' ? current[segment] : undefined;
    }
    return current;
}

// `prebuild.mjs` imports the list as well. The module is plain JavaScript outside of the application, so it is loaded at run time.
const slicePath = resolve('supporting_scripts/client/i18n-slice.mjs');
const { LANDING_NAMESPACES } = (await import(/* @vite-ignore */ slicePath)) as { LANDING_NAMESPACES: string[] };

const english = loadCatalog('en');
const german = loadCatalog('de');

const isCoveredBySlice = (key: string) => LANDING_NAMESPACES.some((entry) => key === entry || key.startsWith(`${entry}.`));

/** Every string literal of the file that is a key of the catalog: a dotted path, or a top-level string such as `aboutUs`. */
function catalogKeysIn(source: string): string[] {
    const literals = new Set([...source.matchAll(/['"`]([A-Za-z][A-Za-z0-9_-]*(?:\.[A-Za-z0-9_-]+)*)['"`]/g)].map((match) => match[1]));
    return [...literals].filter((literal) => {
        const value = valueAt(english, literal);
        return value !== undefined && (literal.includes('.') || typeof value === 'string');
    });
}

describe('Translation slice of the landing and the sign-in page', () => {
    it.each(SHELL_FILES)('should contain the keys that %s shows', (file) => {
        const keys = catalogKeysIn(readFileSync(`src/main/webapp/${file}`, 'utf8'));

        expect(keys.filter((key) => !isCoveredBySlice(key))).toEqual([]);
    });

    it.each(LANDING_NAMESPACES)('should only name an entry that exists in both languages: %s', (entry) => {
        expect(valueAt(english, entry)).toBeDefined();
        expect(valueAt(german, entry)).toBeDefined();
    });

    it('should find the keys of the footer, which are top-level strings of the catalog', () => {
        const keys = catalogKeysIn(readFileSync('src/main/webapp/app/core/layouts/footer/footer.component.html', 'utf8'));

        expect(keys).toEqual(expect.arrayContaining(['aboutUs', 'feedback', 'releases']));
    });
});
