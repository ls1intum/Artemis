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
const serveTsconfig = JSON.parse(readFileSync(resolve(repoRoot, 'tsconfig.serve.json'), 'utf8'));
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
const packageTypeScriptFiles = globSync('packages/tum-ui/src/**/*.ts', { cwd: repoRoot }).filter((file) => !file.endsWith('.spec.ts') && !file.endsWith('.stories.ts'));
const storyFiles = globSync('packages/tum-ui/src/**/*.stories.ts', { cwd: repoRoot });
const storySources = storyFiles.map((file) => readFileSync(resolve(repoRoot, file), 'utf8'));
const packageRuntimeSources = globSync(['packages/tum-ui/src/**/*.{html,scss,ts}'], { cwd: repoRoot })
    .filter((file) => !file.endsWith('.spec.ts') && !file.endsWith('.stories.ts'))
    .map((file) => readFileSync(resolve(repoRoot, file), 'utf8'))
    .join('\n');
const packageStyles = globSync(['packages/tum-ui/**/*.css', 'packages/tum-ui/**/*.scss'], { cwd: repoRoot })
    .map((file) => readFileSync(resolve(repoRoot, file), 'utf8'))
    .join('\n');
const storybookTheme = readFileSync(resolve(repoRoot, 'packages/tum-ui/.storybook/theme.css'), 'utf8');
const hostTheme = readFileSync(resolve(repoRoot, 'src/main/webapp/tailwind.css'), 'utf8');
const workspace = readFileSync(resolve(repoRoot, 'pnpm-workspace.yaml'), 'utf8');
const catalogBlock = workspace.match(/^catalog:\n(?<entries>(?: {2,4}.+\n)+)/m)?.groups?.entries ?? '';
const catalog = Object.fromEntries(
    [...catalogBlock.matchAll(/^ {2,4}(?<name>'[^']+'|[^:]+): (?<version>\S+)$/gm)].map(({ groups }) => [groups.name.replaceAll("'", '').trim(), groups.version]),
);
const namedCatalogs = Object.fromEntries(
    [...workspace.matchAll(/^ {2,4}(?<catalog>[\w-]+):\n(?<entries>(?: {4,8}.+\n)+)/gm)].map(({ groups }) => [
        groups.catalog,
        Object.fromEntries(
            [...groups.entries.matchAll(/^ {4,8}(?<name>'[^']+'|[^:]+): (?<version>\S+)$/gm)].map(({ groups: entry }) => [entry.name.replaceAll("'", '').trim(), entry.version]),
        ),
    ]),
);

function customProperties(marker) {
    const start = storybookTheme.indexOf(marker);
    const openingBrace = storybookTheme.indexOf('{', start);
    const closingBrace = storybookTheme.indexOf('}', openingBrace);
    return Object.fromEntries(
        [...storybookTheme.slice(openingBrace + 1, closingBrace).matchAll(/(--[\w-]+):\s*(#[\da-f]{6});/gi)].map((match) => [match[1], match[2]]),
    );
}

function relativeLuminance(color) {
    const channels = color
        .slice(1)
        .match(/.{2}/g)
        .map((channel) => {
            const value = Number.parseInt(channel, 16) / 255;
            return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
        });
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrastRatio(first, second) {
    const lighter = Math.max(relativeLuminance(first), relativeLuminance(second));
    const darker = Math.min(relativeLuminance(first), relativeLuminance(second));
    return (lighter + 0.05) / (darker + 0.05);
}

function dependencyCatalog(specifier) {
    const name = specifier.slice('catalog:'.length);
    return name ? namedCatalogs[name] : catalog;
}

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
        const dependencies = { ...packageJson.peerDependencies, ...packageJson.dependencies };
        const rootDependencies = { ...rootPackageJson.dependencies, ...rootPackageJson.devDependencies };

        expect(Object.keys(catalog).length).toBeGreaterThan(0);
        for (const [name, version] of Object.entries(dependencies)) {
            expect(catalog[name], `${name} must have one canonical workspace version`).toBe(version);
            expect(rootDependencies[name], `${name} must be shared with Artemis through the catalog`).toBe('catalog:');
            expect(version, `${name} must be valid in the ng-packagr output`).not.toMatch(/^(?:catalog|workspace):/);
        }
        for (const [name, version] of Object.entries(packageJson.devDependencies)) {
            expect(version, `${name} is package-only tooling and must use a workspace catalog`).toMatch(/^catalog:(?:[\w-]+)?$/);
            expect(dependencyCatalog(version)?.[name], `${name} must exist in its selected workspace catalog`).toBeDefined();
            if (rootDependencies[name] && version === 'catalog:') {
                expect(rootDependencies[name], `${name} must use the same workspace catalog entry`).toBe('catalog:');
            }
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

    it('keeps production on the built entry point and development on the public source entry point', () => {
        expect(rootTsconfig.compilerOptions.paths['@tumaet/ui-angular']).toEqual(['./dist/tum-ui']);
        expect(rootTsconfig.compilerOptions.paths['@tumaet/ui-angular/*']).toBeUndefined();
        expect(serveTsconfig.compilerOptions.paths['@tumaet/ui-angular']).toEqual(['./packages/tum-ui/src/public-api.ts']);
        expect(angularWorkspace.projects.artemis.architect.build.configurations['tum-ui-source'].tsConfig).toBe('tsconfig.serve.json');
        expect(angularWorkspace.projects.artemis.architect.serve.options.buildTarget).toBe('artemis:build:development,tum-ui-source');
        expect(angularWorkspace.projects.artemis.architect.serve.options.prebundle.exclude).toContain('@tumaet/ui-angular');
        expect(rootPackageJson.scripts.start).toContain('tum-ui:build:styles -- --watch');
        expect(rootPackageJson.scripts['tum-ui:build:styles']).toContain('--output src/main/webapp/generated/tum-ui.css');
        expect(rootPackageJson.scripts.start).not.toContain('tum-ui:build:watch');
        expect(publicApi).not.toMatch(/export\s+\*\s+from/);
    });

    it('loads the validated package stylesheet after host framework styles', () => {
        const styles = angularWorkspace.projects.artemis.architect.build.options.styles;
        const packageStylesheet = 'src/main/webapp/generated/tum-ui.css';

        expect(styles.filter((style) => style === packageStylesheet)).toHaveLength(1);
        expect(styles.indexOf(packageStylesheet)).toBeGreaterThan(styles.indexOf('src/main/webapp/content/scss/themes/theme-default.scss'));
        expect(styles.indexOf(packageStylesheet)).toBeGreaterThan(styles.indexOf('src/main/webapp/tailwind.css'));
        expect(rootPackageJson.scripts['tum-ui:build']).toContain('tum-ui:check:styles');
        expect(rootPackageJson.scripts['tum-ui:build:dev']).toContain('tum-ui:check:styles');
    });

    it('prefixes package-owned utility class strings', () => {
        const templateClasses = [...packageTemplates.matchAll(/(?<!\[)class="([^"]*)"/g)].flatMap((match) => tokens(match[1]));
        const unnamespacedTemplateClasses = templateClasses.filter((token) => token && !token.startsWith('tum:') && !token.startsWith('tum-ui-'));
        const typeScriptClasses = packageTypeScriptFiles.flatMap(classLikeTypeScriptStrings).flatMap(tokens);
        const unprefixedUtilities = [...templateClasses, ...typeScriptClasses].filter((token) => isUtility(token) && !token.startsWith('tum:'));

        expect(packageTailwind).toContain('prefix(tum)');
        expect(packageTailwind).toContain("@source not './src/**/*.spec.ts'");
        expect(packageTailwind).toContain("@source not './src/**/*.stories.ts'");
        expect(unnamespacedTemplateClasses).toEqual([]);
        expect(unprefixedUtilities).toEqual([]);
    });

    it('keeps package stories isolated and uniquely titled', () => {
        expect(storyFiles.length).toBeGreaterThan(0);
        const titles = [];
        for (const source of storySources) {
            expect(source).not.toMatch(/\btum:/);
            expect(source).not.toMatch(/\bglobals\s*:\s*\{[^}]*\btheme\s*:/s);
            const title = source.match(/\btitle:\s*'([^']+)'/)?.[1];
            expect(title).toBeTruthy();
            titles.push(title);
        }
        expect(new Set(titles).size).toBe(titles.length);
    });

    it('does not ship test-only selectors in package markup', () => {
        expect(packageRuntimeSources).not.toContain('data-testid');
    });

    it('keeps the package theme namespaced and fully mapped by Artemis', () => {
        const consumedProperties = [...packageStyles.matchAll(/var\((--[\w-]+)/g)].map((match) => match[1]);
        const packageProperties = [...new Set([...packageStyles.matchAll(/--tum-ui-[\w-]+/g)].map((match) => match[0]))].sort();
        const hostProperties = [...new Set([...hostTheme.matchAll(/--tum-ui-[\w-]+/g)].map((match) => match[0]))].sort();
        const storybookProperties = [...new Set([...storybookTheme.matchAll(/--tum-ui-[\w-]+/g)].map((match) => match[0]))]
            .filter((property) => property !== '--tum-ui-storybook-canvas')
            .sort();
        const themeColors = new Set([...packageStyles.matchAll(/--color-(tum-ui-[\w-]+)\s*:/g)].map((match) => match[1]));
        const utilityColors = [...new Set(packageRuntimeSources.split(/\s+/).map(themeColorFromUtilityToken).filter(Boolean))].sort();
        const hostColorUtilities = [
            ...packageRuntimeSources.matchAll(/(?:bg|text|border(?:-[trblxy])?|outline|ring(?:-offset)?)-(primary|surface(?:-[\w-]+)?|muted-color|state-[\w-]+)/g),
        ].map((match) => match[0]);

        expect(themeColorFromUtilityToken(`tum:${'&:'.repeat(10_000)}`)).toBeUndefined();
        expect(consumedProperties.filter((property) => !property.startsWith('--tum-ui-'))).toEqual([]);
        expect(packageProperties.filter((property) => property.startsWith('--tum-ui-surface-'))).toEqual([]);
        expect(packageRuntimeSources).not.toContain('tum:dark:');
        expect(packageRuntimeSources).not.toContain(":host-context(html[data-theme='dark'])");
        expect(hostProperties).toEqual(packageProperties);
        expect(storybookProperties).toEqual(packageProperties);
        expect(utilityColors.filter((color) => !themeColors.has(color))).toEqual([]);
        expect(hostColorUtilities).toEqual([]);
    });

    it('keeps the reference themes readable', () => {
        const themes = {
            light: customProperties("[data-theme='light']"),
            dark: customProperties("[data-theme='dark']"),
        };
        const pairs = [
            ['--tum-ui-primary-contrast', '--tum-ui-primary', 4.5],
            ['--tum-ui-text-color', '--tum-ui-content-background', 4.5],
            ['--tum-ui-muted-color', '--tum-ui-content-background', 4.5],
            ['--tum-ui-highlight-color', '--tum-ui-highlight-background', 4.5],
            ['--tum-ui-contrast-color', '--tum-ui-contrast-background', 4.5],
            ['--tum-ui-tooltip-color', '--tum-ui-tooltip-background', 4.5],
            ['--tum-ui-control-border-color', '--tum-ui-control-background', 3],
            ['--tum-ui-control-border-hover-color', '--tum-ui-control-background', 3],
            ...['danger', 'success', 'warning', 'info'].map((state) => [`--tum-ui-state-${state}-contrast`, `--tum-ui-state-${state}`, 4.5]),
        ];

        for (const [theme, properties] of Object.entries(themes)) {
            for (const [foreground, background, minimum] of pairs) {
                const actual = contrastRatio(properties[foreground], properties[background]);
                expect(actual, `${theme}: ${foreground} on ${background}`).toBeGreaterThanOrEqual(minimum);
            }
        }
    });
});
