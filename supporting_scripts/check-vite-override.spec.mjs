import { afterEach, describe, expect, it, vi } from 'vitest';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { checkViteOverride, runViteOverrideCheck } from './check-vite-override.mjs';

/**
 * Builds a throwaway directory with a synthetic `node_modules` tree so the check can be pointed at
 * a controlled resolution root instead of the repo's real install.
 *
 * @param {{angularBuildVersion?: string, angularBuildVite?: string | null, installedVite?: string | null, hideVitePackageJson?: boolean}} tree
 *     `angularBuildVite` is what `@angular/build` declares (null omits the package entirely);
 *     `installedVite` is the version actually installed; `hideVitePackageJson` gives that package an
 *     `exports` map without `./package.json`, the way `vite-tsconfig-paths` already ships.
 * @returns {string} The directory to pass as `resolveFrom`.
 */
function createTree({ angularBuildVersion = '22.1.1', angularBuildVite, installedVite, hideVitePackageJson = false }) {
    const root = mkdtempSync(join(tmpdir(), 'vite-override-check-'));
    createdRoots.push(root);

    if (angularBuildVite !== null) {
        const angularBuild = join(root, 'node_modules', '@angular', 'build');
        mkdirSync(angularBuild, { recursive: true });
        const dependencies = angularBuildVite === undefined ? {} : { vite: angularBuildVite };
        writeFileSync(join(angularBuild, 'package.json'), JSON.stringify({ name: '@angular/build', version: angularBuildVersion, dependencies }));
    }

    const vite = join(root, 'node_modules', 'vite');
    mkdirSync(vite, { recursive: true });
    const manifest = { name: 'vite', version: installedVite };
    if (hideVitePackageJson) {
        manifest.exports = { '.': './index.js' };
        writeFileSync(join(vite, 'index.js'), 'module.exports = {};');
    }
    writeFileSync(join(vite, 'package.json'), JSON.stringify(manifest));

    return root;
}

const createdRoots = [];

afterEach(() => {
    while (createdRoots.length) {
        rmSync(createdRoots.pop(), { recursive: true, force: true });
    }
});

describe('checkViteOverride', () => {
    it('passes when the installed vite matches what @angular/build declares', () => {
        const root = createTree({ angularBuildVite: '8.1.5', installedVite: '8.1.5' });

        expect(checkViteOverride({ resolveFrom: root })).toEqual({ checked: true });
    });

    it('passes against this repository, so a bad override fails the test suite too', () => {
        // No `resolveFrom`: this exercises the default resolution used by prebuild.mjs and asserts
        // the committed lockfile gives @angular/build its exact Vite pin or the reviewed security patch.
        expect(checkViteOverride()).toEqual({ checked: true });
    });

    it('allows the reviewed Vite security patch for Angular builder 22.2.3', () => {
        const root = createTree({ angularBuildVersion: '22.2.3', angularBuildVite: '8.3.0', installedVite: '8.3.3' });

        expect(checkViteOverride({ resolveFrom: root })).toEqual({ checked: true });
    });

    it.each([
        { angularBuildVersion: '22.2.4', angularBuildVite: '8.3.0', installedVite: '8.3.3' },
        { angularBuildVersion: '22.2.3', angularBuildVite: '8.3.1', installedVite: '8.3.3' },
        { angularBuildVersion: '22.2.3', angularBuildVite: '8.3.0', installedVite: '8.3.4' },
        { angularBuildVersion: '22.2.3', angularBuildVite: '8.3.0', installedVite: '8.4.0' },
    ])('rejects an unreviewed builder/Vite combination: $angularBuildVersion / $angularBuildVite / $installedVite', (tree) => {
        const root = createTree(tree);

        expect(() => checkViteOverride({ resolveFrom: root })).toThrow(/is installed for @angular\/build/);
    });

    it('throws with an actionable message when an override downgrades vite', () => {
        const root = createTree({ angularBuildVite: '8.1.5', installedVite: '7.3.6' });

        // This is the #13359 regression: the dev server silently stops linking pre-bundled deps.
        expect(() => checkViteOverride({ resolveFrom: root })).toThrow(/vite 7\.3\.6 is installed for @angular\/build, which depends on vite 8\.1\.5/);
        expect(() => checkViteOverride({ resolveFrom: root })).toThrow(/remove the vite override from pnpm-workspace\.yaml \(or set it to '8\.1\.5'\)/);
    });

    it('throws when an override pins vite above the declared version', () => {
        const root = createTree({ angularBuildVite: '8.1.5', installedVite: '8.2.0' });

        expect(() => checkViteOverride({ resolveFrom: root })).toThrow(/vite 8\.2\.0 is installed/);
    });

    it('accepts an exact prerelease version', () => {
        const root = createTree({ angularBuildVite: '8.2.0-beta.1', installedVite: '8.2.0-beta.1' });

        expect(checkViteOverride({ resolveFrom: root })).toEqual({ checked: true });
    });

    it('skips when @angular/build is not installed', () => {
        const root = createTree({ angularBuildVite: null, installedVite: '8.1.5' });

        expect(checkViteOverride({ resolveFrom: root })).toEqual({ checked: false, reason: '@angular/build is not installed' });
    });

    it('skips when @angular/build no longer declares a vite dependency', () => {
        const root = createTree({ angularBuildVite: undefined, installedVite: '8.1.5' });

        expect(checkViteOverride({ resolveFrom: root })).toEqual({ checked: false, reason: '@angular/build no longer declares a vite dependency' });
    });

    it('skips when @angular/build declares a range instead of an exact version', () => {
        const root = createTree({ angularBuildVite: '^8.1.5', installedVite: '8.2.0' });

        // Comparing a range against a resolved version as strings would be a false alarm.
        expect(checkViteOverride({ resolveFrom: root })).toEqual({ checked: false, reason: '@angular/build declares a vite range ("^8.1.5") rather than an exact version' });
    });

    it('skips when vite does not expose its package.json instead of aborting the build', () => {
        const root = createTree({ angularBuildVite: '8.1.5', installedVite: '8.1.5', hideVitePackageJson: true });

        // prebuild.mjs prints only the message, so letting ERR_PACKAGE_PATH_NOT_EXPORTED escape would
        // break every client build with no hint about the cause.
        expect(checkViteOverride({ resolveFrom: root })).toEqual({ checked: false, reason: 'vite could not be resolved from @angular/build' });
    });
});

describe('runViteOverrideCheck', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('reports a match without exiting', () => {
        const root = createTree({ angularBuildVite: '8.1.5', installedVite: '8.1.5' });
        const log = vi.spyOn(console, 'log').mockImplementation(() => {});
        const exit = vi.spyOn(process, 'exit').mockImplementation(() => {});

        runViteOverrideCheck({ resolveFrom: root });

        expect(log).toHaveBeenCalledWith('vite matches the required or reviewed security-patched version for @angular/build.');
        expect(exit).not.toHaveBeenCalled();
    });

    it('reports the reason on a skip rather than claiming a match', () => {
        const root = createTree({ angularBuildVite: null, installedVite: '8.1.5' });
        const log = vi.spyOn(console, 'log').mockImplementation(() => {});
        const exit = vi.spyOn(process, 'exit').mockImplementation(() => {});

        runViteOverrideCheck({ resolveFrom: root });

        expect(log).toHaveBeenCalledWith('Skipped vite override check: @angular/build is not installed.');
        expect(exit).not.toHaveBeenCalled();
    });

    it('prints only the message and exits non-zero on a mismatch', () => {
        const root = createTree({ angularBuildVite: '8.1.5', installedVite: '7.3.6' });
        const log = vi.spyOn(console, 'log').mockImplementation(() => {});
        const error = vi.spyOn(console, 'error').mockImplementation(() => {});
        // prebuild.mjs relies on this exit; without the mock the test worker would be torn down.
        const exit = vi.spyOn(process, 'exit').mockImplementation(() => {});

        runViteOverrideCheck({ resolveFrom: root });

        expect(exit).toHaveBeenCalledWith(1);
        expect(error).toHaveBeenCalledWith(expect.stringContaining('vite 7.3.6 is installed for @angular/build'));
        // No success line may follow the failure, even though the mocked exit does not unwind.
        expect(log).not.toHaveBeenCalled();
    });
});
