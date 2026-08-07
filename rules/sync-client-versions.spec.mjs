import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { afterEach, describe, expect, it } from 'vitest';
import { parse } from 'yaml';
import { parseEnv, syncForward, syncReverse } from '../supporting_scripts/sync-client-versions.mjs';

const temporaryRoots = [];

function fixture({ rootPackage, workspace, playwrightVersion = '1.61.1', env = {} }) {
    const root = mkdtempSync(resolve(tmpdir(), 'artemis-version-sync-'));
    temporaryRoots.push(root);
    const playwrightDirectory = resolve(root, 'src/test/playwright');
    mkdirSync(playwrightDirectory, { recursive: true });
    const paths = {
        envFile: resolve(root, '.env'),
        rootPackage: resolve(root, 'package.json'),
        playwrightPackage: resolve(playwrightDirectory, 'package.json'),
        workspace: resolve(root, 'pnpm-workspace.yaml'),
    };
    writeFileSync(
        paths.envFile,
        Object.entries(env)
            .map(([name, value]) => `${name}=${value}`)
            .join('\n') + '\n',
    );
    writeFileSync(paths.rootPackage, JSON.stringify(rootPackage, null, 4) + '\n');
    writeFileSync(paths.playwrightPackage, JSON.stringify({ devDependencies: { '@playwright/test': playwrightVersion } }, null, 4) + '\n');
    writeFileSync(paths.workspace, workspace);
    return paths;
}

afterEach(() => {
    for (const root of temporaryRoots.splice(0)) {
        rmSync(root, { recursive: true, force: true });
    }
});

describe('client dependency version synchronization', () => {
    it('reports catalog and manifest drift without changing files in check mode', () => {
        const paths = fixture({
            env: { ANGULAR_VERSION: '22.0.8', PLAYWRIGHT_VERSION: 'v1.61.1' },
            rootPackage: { dependencies: { '@angular/core': 'catalog:', '@playwright/test': 'catalog:' } },
            workspace: "# retained\ncatalog:\n  '@angular/core': 22.0.7\n",
        });
        const before = Object.fromEntries(Object.entries(paths).map(([name, path]) => [name, readFileSync(path, 'utf8')]));

        expect(syncForward(parseEnv(paths.envFile), true, paths)).toBe(1);
        expect(Object.fromEntries(Object.entries(paths).map(([name, path]) => [name, readFileSync(path, 'utf8')]))).toEqual(before);
    });

    it('updates default catalog, missing entries, direct versions, and Playwright together', () => {
        const paths = fixture({
            env: { ANGULAR_VERSION: '22.0.8', PLAYWRIGHT_VERSION: 'v1.61.1', TYPESCRIPT_ESLINT_VERSION: '8.65.0' },
            rootPackage: {
                dependencies: { '@angular/core': 'catalog:', '@playwright/test': 'catalog:' },
                devDependencies: { 'typescript-eslint': '8.64.0' },
            },
            workspace: "# retained\ncatalog:\n  '@angular/core': 22.0.7\n",
            playwrightVersion: '1.60.0',
        });

        expect(syncForward(parseEnv(paths.envFile), false, paths)).toBe(0);
        expect(readFileSync(paths.workspace, 'utf8')).toContain('# retained');
        expect(parse(readFileSync(paths.workspace, 'utf8')).catalog['@playwright/test']).toBe('1.61.1');
        expect(JSON.parse(readFileSync(paths.rootPackage, 'utf8')).devDependencies['typescript-eslint']).toBe('8.65.0');
        expect(JSON.parse(readFileSync(paths.playwrightPackage, 'utf8')).devDependencies['@playwright/test']).toBe('1.61.1');
    });

    it('reverse-syncs named catalogs and normalizes the Playwright Docker tag', () => {
        const paths = fixture({
            env: { ANGULAR_VERSION: '22.0.7', PLAYWRIGHT_VERSION: 'v1.60.0' },
            rootPackage: { dependencies: { '@angular/core': 'catalog:framework' } },
            workspace: "catalogs:\n  framework:\n    '@angular/core': 22.0.8\n",
            playwrightVersion: '1.61.1',
        });

        expect(syncReverse(parseEnv(paths.envFile), paths)).toBe(0);
        expect(parseEnv(paths.envFile)).toMatchObject({ ANGULAR_VERSION: '22.0.8', PLAYWRIGHT_VERSION: 'v1.61.1' });
    });
});
