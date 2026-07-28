import { globSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const packageJson = JSON.parse(readFileSync(resolve(repoRoot, 'packages/tum-ui/package.json'), 'utf8'));
const rootPackageJson = JSON.parse(readFileSync(resolve(repoRoot, 'package.json'), 'utf8'));
const productionBuild = JSON.parse(readFileSync(resolve(repoRoot, 'packages/tum-ui/ng-package.json'), 'utf8'));
const developmentBuild = JSON.parse(readFileSync(resolve(repoRoot, 'packages/tum-ui/ng-package.dev.json'), 'utf8'));
const rootTsconfigPath = resolve(repoRoot, 'tsconfig.json');
const { config: rootTsconfig, error: rootTsconfigError } = ts.readConfigFile(rootTsconfigPath, ts.sys.readFile);
if (rootTsconfigError) {
    throw new Error(ts.flattenDiagnosticMessageText(rootTsconfigError.messageText, '\n'));
}
const publicApi = readFileSync(resolve(repoRoot, 'packages/tum-ui/src/public-api.ts'), 'utf8');
const packageStyles = globSync(['packages/tum-ui/**/*.css', 'packages/tum-ui/**/*.scss'], { cwd: repoRoot })
    .map((file) => readFileSync(resolve(repoRoot, file), 'utf8'))
    .join('\n');
const hostTheme = readFileSync(resolve(repoRoot, 'src/main/webapp/tailwind.css'), 'utf8');
const workspace = readFileSync(resolve(repoRoot, 'pnpm-workspace.yaml'), 'utf8');
const catalogBlock = workspace.match(/^catalog:\n(?<entries>(?: {2}.+\n)+)/m)?.groups?.entries ?? '';
const catalog = Object.fromEntries(
    [...catalogBlock.matchAll(/^ {2}(?<name>'[^']+'|[^:]+): (?<version>\S+)$/gm)].map(({ groups }) => [groups.name.replaceAll("'", '').trim(), groups.version]),
);

describe('@tumaet/ui-angular package manifest', () => {
    it('pins every dependency to the root pnpm catalog version', () => {
        const dependencies = { ...packageJson.peerDependencies, ...packageJson.dependencies };
        const rootDependencies = { ...rootPackageJson.dependencies, ...rootPackageJson.devDependencies };

        expect(Object.keys(catalog).length).toBeGreaterThan(0);
        for (const [name, version] of Object.entries(dependencies)) {
            expect(catalog[name], `${name} must have one canonical workspace version`).toBe(version);
            expect(rootDependencies[name], `${name} must be shared with Artemis through the catalog`).toBe('catalog:');
            expect(version, `${name} must be valid in the ng-packagr output`).not.toMatch(/^(?:catalog|workspace):/);
        }
    });

    it('keeps development and production artifacts identical except for preserving watch output', () => {
        const { deleteDestPath, ...developmentArtifact } = developmentBuild;

        expect(deleteDestPath).toBe(false);
        expect(developmentArtifact).toEqual(productionBuild);
    });

    it('keeps the package private and preserves its explicit stylesheet import', () => {
        expect(packageJson.private).toBe(true);
        expect(packageJson.sideEffects).toEqual(['./theme.css']);
        expect(packageJson.exports).toEqual({ './theme.css': './theme.css' });
        expect(productionBuild.assets).toContain('theme.css');
    });

    it('makes Artemis consume only the built public entry point', () => {
        expect(rootTsconfig.compilerOptions.paths['@tumaet/ui-angular']).toEqual(['./dist/tum-ui']);
        expect(rootTsconfig.compilerOptions.paths['@tumaet/ui-angular/*']).toBeUndefined();
        expect(publicApi).not.toMatch(/export\s+\*\s+from/);
    });

    it('keeps the package theme namespaced and fully mapped by Artemis', () => {
        const consumedProperties = [...packageStyles.matchAll(/var\((--[\w-]+)/g)].map((match) => match[1]);
        const packageProperties = [...new Set([...packageStyles.matchAll(/--tum-ui-[\w-]+/g)].map((match) => match[0]))].sort();
        const hostProperties = [...new Set([...hostTheme.matchAll(/--tum-ui-[\w-]+/g)].map((match) => match[0]))].sort();

        expect(consumedProperties.filter((property) => !property.startsWith('--tum-ui-'))).toEqual([]);
        expect(hostProperties).toEqual(packageProperties);
    });
});
