import { existsSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { ICON_PACKAGES, OUTPUT_DIRECTORY, PROVENANCE_FILE, generateIconModules, iconModuleSource, iconNames } from './fontawesome-icons.mjs';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const require = createRequire(join(repoRoot, 'package.json'));

function packageDirectory(packageName) {
    return dirname(require.resolve(`@fortawesome/${packageName}/package.json`));
}

/** The name and the icon data of a generated module: the module has one exported constant, whose value is a JSON object. */
function parse(source) {
    const match = /^export const (\w+) = (\{.*\}) as unknown as IconDefinition;$/m.exec(source);
    return { name: match[1], definition: JSON.parse(match[2]) };
}

/**
 * Icons that the package root exports under a deprecated name, and that the package has no module of its own for. The migration reports them, the
 * application does not use them, and a name that the package adds or removes shows up here.
 */
const DEPRECATED_ROOT_ONLY_ICONS = {
    'free-solid-svg-icons': ['faEyedropper', 'faPaintBrush', 'faThumbTack', 'faThumbTackSlash', 'faTshirt'],
    'free-regular-svg-icons': [],
};

describe('FontAwesome icon modules', () => {
    it.each([
        ['free-solid-svg-icons', 'faTrash'],
        ['free-solid-svg-icons', 'faTimes'], // an alias: its module has the data of the icon that it stands for
        ['free-solid-svg-icons', 'fa0'],
        ['free-regular-svg-icons', 'faCircleCheck'],
    ])('the module of %s/%s exports the data that the package exports for it', (packageName, name) => {
        const { version } = require(`@fortawesome/${packageName}/package.json`);
        const definition = require(`@fortawesome/${packageName}/${name}`)[name];

        const generated = parse(iconModuleSource(name, definition, packageName, version));

        expect(generated.name).toBe(name);
        expect(generated.definition).toEqual({ prefix: definition.prefix, iconName: definition.iconName, icon: definition.icon });
    });

    it('writes a type import only, so that a module has no runtime dependency', () => {
        const source = iconModuleSource('faTrash', require('@fortawesome/free-solid-svg-icons/faTrash').faTrash, 'free-solid-svg-icons', '7.0.0');

        expect(source).toContain(`import type { IconDefinition } from '@fortawesome/fontawesome-svg-core';`);
        expect(source.match(/^import /gm)).toHaveLength(1);
        expect(source).toContain('export const faTrash = ');
        expect(source).toContain(' as unknown as IconDefinition;');
    });

    describe.each(ICON_PACKAGES)('%s', (packageName) => {
        it('has a module of its own for every icon that the package root exports, except the deprecated names that only the root has', () => {
            const rootExports = require(`@fortawesome/${packageName}`);
            const exportedIcons = Object.keys(rootExports).filter((name) => /^fa[A-Z0-9]/.test(name) && rootExports[name]?.icon);
            const available = new Set(iconNames(packageDirectory(packageName)));

            expect(exportedIcons.filter((name) => !available.has(name)).sort()).toEqual(DEPRECATED_ROOT_ONLY_ICONS[packageName]);
        });

        it('names only icons: not the index, not the declarations', () => {
            const names = iconNames(packageDirectory(packageName));

            expect(names.length).toBeGreaterThan(100);
            expect(names.every((name) => /^fa[A-Z0-9][A-Za-z0-9]*$/.test(name))).toBe(true);
            expect(names).not.toContain('index');
        });
    });

    it('writes the modules of the icons that the Angular build maps the imports to, and does nothing when they are up to date', () => {
        generateIconModules();

        expect(generateIconModules()).toBe(0);
        const solid = readFileSync(join(repoRoot, OUTPUT_DIRECTORY, 'free-solid-svg-icons', 'faTrash.ts'), 'utf8');
        expect(solid).toContain('export const faTrash = ');
        expect(existsSync(join(repoRoot, OUTPUT_DIRECTORY, 'free-regular-svg-icons', 'faCircleCheck.ts'))).toBe(true);
    });

    it('records the version of each package next to the modules, for the SBOM of a release', () => {
        generateIconModules();

        const provenance = JSON.parse(readFileSync(join(repoRoot, OUTPUT_DIRECTORY, PROVENANCE_FILE), 'utf8'));

        expect(provenance).toEqual(
            Object.fromEntries(ICON_PACKAGES.map((packageName) => [`@fortawesome/${packageName}`, require(`@fortawesome/${packageName}/package.json`).version])),
        );
    });
});
