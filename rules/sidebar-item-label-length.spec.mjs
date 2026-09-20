import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const webapp = join(repoRoot, 'src/main/webapp');

/**
 * The navigation sidebar of the student overview, course management and administration shells is sized to the
 * narrowest width that still shows every item label in full, in English and German (`--sidebar-nav-width`). A longer
 * label would therefore be cut off rather than widen the sidebar, so this keeps every label within the documented cap.
 *
 * Character count is a proxy — glyph widths differ, and a short label with wide letters can be wider than a longer one
 * — which is why the width carries headroom over the measured minimum. The cap catches the case that actually happens:
 * someone adds a label noticeably longer than anything the sidebar was measured against.
 */
const SIDEBAR_ITEM_SOURCES = [
    'app/course/shared/services/sidebar-item.service.ts',
    'app/admin/admin-sidebar/admin-sidebar.component.ts',
    'app/course/manage/course-management-container/course-management-container.component.ts',
    'app/course/overview/course-overview/course-overview.component.ts',
    'app/course/shared/course-sidebar/course-sidebar.component.ts',
];

const LANGUAGES = ['en', 'de'];

const readCap = () => {
    const src = readFileSync(join(webapp, 'app/course/shared/course-sidebar/course-sidebar.component.ts'), 'utf8');
    const match = src.match(/MAX_SIDEBAR_ITEM_LABEL_LENGTH\s*=\s*(\d+)/);
    expect(match, 'MAX_SIDEBAR_ITEM_LABEL_LENGTH is not declared in course-sidebar.component.ts').toBeTruthy();
    return Number(match[1]);
};

const bundleFor = (lang) => {
    const dir = join(webapp, 'i18n', lang);
    const merged = {};
    const deepMerge = (target, source) => {
        for (const [key, value] of Object.entries(source)) {
            if (value && typeof value === 'object' && !Array.isArray(value)) {
                target[key] = target[key] && typeof target[key] === 'object' ? target[key] : {};
                deepMerge(target[key], value);
            } else {
                target[key] = value;
            }
        }
    };
    for (const file of readdirSync(dir)) {
        if (file.endsWith('.json')) deepMerge(merged, JSON.parse(readFileSync(join(dir, file), 'utf8')));
    }
    return merged;
};

const translationKeys = () => {
    const keys = new Set();
    for (const rel of SIDEBAR_ITEM_SOURCES) {
        const src = readFileSync(join(webapp, rel), 'utf8');
        for (const match of src.matchAll(/translation:\s*'([^']+)'/g)) keys.add(match[1]);
    }
    return [...keys].sort();
};

const lookup = (bundle, key) => key.split('.').reduce((node, part) => (node == null ? undefined : node[part]), bundle);

describe('sidebar item labels fit the navigation sidebar', () => {
    const cap = readCap();
    const keys = translationKeys();
    const bundles = Object.fromEntries(LANGUAGES.map((lang) => [lang, bundleFor(lang)]));

    it('finds the sidebar item translation keys', () => {
        // A rename that silently emptied this list would make every assertion below pass for the wrong reason.
        expect(keys.length).toBeGreaterThan(20);
        expect(keys).toContain('artemisApp.courseOverview.menu.exercises');
        expect(keys).toContain('global.menu.admin.sidebar.users');
    });

    it.each(LANGUAGES)('resolves every sidebar item label in %s', (lang) => {
        const missing = keys.filter((key) => typeof lookup(bundles[lang], key) !== 'string');
        expect(missing, `sidebar items without a ${lang} translation: ${missing.join(', ')}`).toEqual([]);
    });

    it.each(LANGUAGES)('keeps every %s sidebar item label within the cap', (lang) => {
        const tooLong = keys
            .map((key) => ({ key, label: lookup(bundles[lang], key) }))
            .filter(({ label }) => typeof label === 'string' && label.length > cap)
            .map(({ key, label }) => `${label} (${label.length} chars, ${key})`);

        expect(
            tooLong,
            `These ${lang} sidebar labels exceed MAX_SIDEBAR_ITEM_LABEL_LENGTH (${cap}) and would be cut off, because ` +
                `--sidebar-nav-width is the narrowest width that shows the measured labels in full. Shorten the label, or ` +
                `widen the sidebar deliberately and raise both together:\n  ${tooLong.join('\n  ')}`,
        ).toEqual([]);
    });
});
