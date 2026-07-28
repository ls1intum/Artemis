import { globSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const packageJson = JSON.parse(readFileSync(resolve(repoRoot, 'packages/tum-ui/package.json'), 'utf8'));
const rootPackageJson = JSON.parse(readFileSync(resolve(repoRoot, 'package.json'), 'utf8'));
const angularWorkspace = JSON.parse(readFileSync(resolve(repoRoot, 'angular.json'), 'utf8'));
const productionBuild = JSON.parse(readFileSync(resolve(repoRoot, 'packages/tum-ui/ng-package.json'), 'utf8'));
const developmentBuild = JSON.parse(readFileSync(resolve(repoRoot, 'packages/tum-ui/ng-package.dev.json'), 'utf8'));
const packBuild = JSON.parse(readFileSync(resolve(repoRoot, 'packages/tum-ui/ng-package.pack.json'), 'utf8'));
const rootTsconfigPath = resolve(repoRoot, 'tsconfig.json');
const { config: rootTsconfig, error: rootTsconfigError } = ts.readConfigFile(rootTsconfigPath, ts.sys.readFile);
if (rootTsconfigError) {
    throw new Error(ts.flattenDiagnosticMessageText(rootTsconfigError.messageText, '\n'));
}
const publicApi = readFileSync(resolve(repoRoot, 'packages/tum-ui/src/public-api.ts'), 'utf8');
const packageTailwind = readFileSync(resolve(repoRoot, 'packages/tum-ui/tailwind.css'), 'utf8');
const packageTemplates = globSync('packages/tum-ui/src/**/*.html', { cwd: repoRoot })
    .map((file) => readFileSync(resolve(repoRoot, file), 'utf8'))
    .join('\n');
const packageTypeScriptFiles = globSync('packages/tum-ui/src/**/*.ts', { cwd: repoRoot, ignore: ['**/*.spec.ts'] });
const packageRuntimeSources = globSync(['packages/tum-ui/src/**/*.{html,scss,ts}'], {
    cwd: repoRoot,
    ignore: ['**/*.spec.ts'],
})
    .map((file) => readFileSync(resolve(repoRoot, file), 'utf8'))
    .join('\n');
const packageStyles = globSync(['packages/tum-ui/**/*.css', 'packages/tum-ui/**/*.scss'], { cwd: repoRoot })
    .map((file) => readFileSync(resolve(repoRoot, file), 'utf8'))
    .join('\n');
const hostTheme = readFileSync(resolve(repoRoot, 'src/main/webapp/tailwind.css'), 'utf8');
const workspace = readFileSync(resolve(repoRoot, 'pnpm-workspace.yaml'), 'utf8');
const catalogBlock = workspace.match(/^catalog:\n(?<entries>(?: {2}.+\n)+)/m)?.groups?.entries ?? '';
const catalog = Object.fromEntries(
    [...catalogBlock.matchAll(/^ {2}(?<name>'[^']+'|[^:]+): (?<version>\S+)$/gm)].map(({ groups }) => [groups.name.replaceAll("'", '').trim(), groups.version]),
);

const exactUtilities = new Set([
    'absolute',
    'relative',
    'fixed',
    'sticky',
    'flex',
    'grid',
    'block',
    'inline-block',
    'inline-flex',
    'hidden',
    'grow',
    'shrink',
    'shrink-0',
    'items-center',
    'justify-between',
    'justify-center',
    'justify-end',
    'appearance-none',
    'pointer-events-none',
    'cursor-pointer',
    'cursor-default',
    'cursor-text',
    'select-none',
    'list-none',
    'border',
    'border-collapse',
    'outline',
    'outline-none',
    'rounded',
    'whitespace-nowrap',
    'truncate',
    'table',
]);
const patternedUtility =
    /^(?:-?(?:m[trblxy]?|p[trblxy]?|w|h|min-[wh]|max-[wh]|top|right|bottom|left|inset(?:-[xy])?|translate-[xy]|z|gap|flex|grid|items|justify|overflow(?:-[xy])?|border(?:-[trblxy])?|rounded|bg|text|font|leading|shadow|outline|ring(?:-offset)?|opacity|transition|duration|animate|whitespace|rotate)(?:-.+)|border-[0-9]+)$/;
const themeColorUtility = /^(?:bg|text|border(?:-[trblxy])?|outline|ring(?:-offset)?)-(tum-ui-[\w-]+)$/;

function isUtility(token) {
    const unprefixed = token.startsWith('tum:') ? token.slice(4) : token;
    const base = unprefixed.split(':').at(-1);
    return exactUtilities.has(base) || patternedUtility.test(base);
}

function tokens(value) {
    return value.split(/\s+/).map((token) => token.replace(/^['"`]|['"`;,)}]+$/g, ''));
}

function themeColorFromUtilityToken(token) {
    const prefixIndex = token.indexOf('tum:');
    if (prefixIndex < 0) {
        return undefined;
    }
    const utility = token
        .slice(prefixIndex)
        .replace(/['"`;,)}\]>]+$/g, '')
        .split(':')
        .at(-1);
    return themeColorUtility.exec(utility)?.[1];
}

function classLikeTypeScriptStrings(file) {
    const source = readFileSync(resolve(repoRoot, file), 'utf8');
    const sourceFile = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true);
    const strings = [];

    function visit(node) {
        if (ts.isStringLiteralLike(node) || ts.isTemplateHead(node) || ts.isTemplateMiddle(node) || ts.isTemplateTail(node)) {
            const ancestors = [];
            for (let current = node.parent; current && ancestors.length < 8; current = current.parent) {
                if ('name' in current && current.name) {
                    ancestors.push(current.name.getText(sourceFile));
                }
            }
            if (node.text.includes('tum:') || node.text.includes('tum-ui-') || ancestors.some((name) => /class|padding|severity|state|size|hover|stripe|base/i.test(name))) {
                strings.push(node.text);
            }
        }
        ts.forEachChild(node, visit);
    }

    visit(sourceFile);
    return strings;
}

describe('@tumaet/ui-angular package manifest', () => {
    it('pins every dependency to the root pnpm catalog version', () => {
        const dependencies = { ...packageJson.peerDependencies, ...packageJson.dependencies, ...packageJson.devDependencies };
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
        const { dest: productionDestination, ...productionArtifact } = productionBuild;
        const { dest: packDestination, ...packArtifact } = packBuild;

        expect(deleteDestPath).toBe(false);
        expect(developmentArtifact).toEqual(productionBuild);
        expect(packArtifact).toEqual(productionArtifact);
        expect(packDestination).not.toBe(productionDestination);
        expect(angularWorkspace.projects['tum-ui'].architect.build.configurations.pack.project).toBe('packages/tum-ui/ng-package.pack.json');
        expect(rootPackageJson.scripts['tum-ui:pack:check']).toContain('dist/tum-ui-pack');
    });

    it('keeps the package private and declares its stylesheet subpath', () => {
        expect(packageJson.private).toBe(true);
        expect(packageJson.sideEffects).toEqual(['./styles.css']);
        expect(packageJson.exports).toEqual({ './styles.css': './styles.css' });
        expect(productionBuild.assets).not.toContain('styles.css');
    });

    it('makes Artemis consume only the built public entry point', () => {
        expect(rootTsconfig.compilerOptions.paths['@tumaet/ui-angular']).toEqual(['./dist/tum-ui']);
        expect(rootTsconfig.compilerOptions.paths['@tumaet/ui-angular/*']).toBeUndefined();
        expect(publicApi).not.toMatch(/export\s+\*\s+from/);
    });

    it('loads the validated package stylesheet after host framework styles', () => {
        const styles = angularWorkspace.projects.artemis.architect.build.options.styles;
        const packageStylesheet = 'dist/tum-ui/styles.css';

        expect(styles.filter((style) => style === packageStylesheet)).toHaveLength(1);
        expect(styles.indexOf(packageStylesheet)).toBeGreaterThan(styles.indexOf('src/main/webapp/content/scss/themes/theme-default.scss'));
        expect(styles.indexOf(packageStylesheet)).toBeGreaterThan(styles.indexOf('src/main/webapp/tailwind.css'));
        expect(rootPackageJson.scripts['tum-ui:build']).toContain('tum-ui:check:styles');
        expect(rootPackageJson.scripts['tum-ui:build:dev']).toContain('tum-ui:check:styles');
    });

    it('prefixes package-owned utility class strings', () => {
        const templateClasses = [...packageTemplates.matchAll(/(?<!\[)class="([^"]*)"/g)].flatMap((match) => tokens(match[1]));
        const typeScriptClasses = packageTypeScriptFiles.flatMap(classLikeTypeScriptStrings).flatMap(tokens);
        const unprefixedUtilities = [...templateClasses, ...typeScriptClasses].filter((token) => isUtility(token) && !token.startsWith('tum:'));

        expect(packageTailwind).toContain('prefix(tum)');
        expect(packageTailwind).toContain("@source not './src/**/*.spec.ts'");
        expect(unprefixedUtilities).toEqual([]);
    });

    it('keeps the package theme namespaced and fully mapped by Artemis', () => {
        const consumedProperties = [...packageStyles.matchAll(/var\((--[\w-]+)/g)].map((match) => match[1]);
        const packageProperties = [...new Set([...packageStyles.matchAll(/--tum-ui-[\w-]+/g)].map((match) => match[0]))].sort();
        const hostProperties = [...new Set([...hostTheme.matchAll(/--tum-ui-[\w-]+/g)].map((match) => match[0]))].sort();
        const themeColors = new Set([...packageStyles.matchAll(/--color-(tum-ui-[\w-]+)\s*:/g)].map((match) => match[1]));
        const utilityColors = [...new Set(packageRuntimeSources.split(/\s+/).map(themeColorFromUtilityToken).filter(Boolean))].sort();
        const hostColorUtilities = [
            ...packageRuntimeSources.matchAll(/(?:bg|text|border(?:-[trblxy])?|outline|ring(?:-offset)?)-(primary|surface(?:-[\w-]+)?|muted-color|state-[\w-]+)/g),
        ].map((match) => match[0]);

        expect(themeColorFromUtilityToken(`tum:${'&:'.repeat(10_000)}`)).toBeUndefined();
        expect(consumedProperties.filter((property) => !property.startsWith('--tum-ui-'))).toEqual([]);
        expect(hostProperties).toEqual(packageProperties);
        expect(utilityColors.filter((color) => !themeColors.has(color))).toEqual([]);
        expect(hostColorUtilities).toEqual([]);
    });
});
