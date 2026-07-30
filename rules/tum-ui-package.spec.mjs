import { globSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';
import { compile } from 'sass';
import ts from 'typescript';
import { describe, expect, it } from 'vitest';
import { parse } from 'yaml';

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
const packageRuntimeSources = globSync(['packages/tum-ui/src/**/*.{html,scss,ts}'], { cwd: repoRoot })
    .filter((file) => !file.endsWith('.spec.ts') && !file.endsWith('.stories.ts'))
    .map((file) => readFileSync(resolve(repoRoot, file), 'utf8'))
    .join('\n');
const packageTemplates = globSync('packages/tum-ui/src/**/*.html', { cwd: repoRoot }).map((file) => ({
    file,
    source: readFileSync(resolve(repoRoot, file), 'utf8'),
}));
const packageStyles = globSync(['packages/tum-ui/src/**/*.{css,scss}', 'packages/tum-ui/tailwind.css', 'packages/tum-ui/themes.css'], { cwd: repoRoot })
    .map((file) => readFileSync(resolve(repoRoot, file), 'utf8'))
    .join('\n');
const storybookTheme = readFileSync(resolve(repoRoot, 'packages/tum-ui/themes.css'), 'utf8');
const hostTheme = readFileSync(resolve(repoRoot, 'src/main/webapp/tailwind.css'), 'utf8');
const workspace = parse(readFileSync(resolve(repoRoot, 'pnpm-workspace.yaml'), 'utf8'));
const catalog = workspace.catalog ?? {};
const namedCatalogs = workspace.catalogs ?? {};

function customProperties(marker) {
    const start = storybookTheme.indexOf(marker);
    const openingBrace = storybookTheme.indexOf('{', start);
    const closingBrace = storybookTheme.indexOf('}', openingBrace);
    return Object.fromEntries(
        [...storybookTheme.slice(openingBrace + 1, closingBrace).matchAll(/(--[\w-]+):\s*(#[\da-f]{3}(?:[\da-f]{3})?);/gi)].map((match) => [match[1], match[2]]),
    );
}

function relativeLuminance(color) {
    const channels = cssColorChannels(color).map((value) => {
        return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function cssColorChannels(color) {
    if (color.startsWith('#')) {
        const normalized = color.length === 4 ? `#${[...color.slice(1)].map((channel) => channel.repeat(2)).join('')}` : color;
        return normalized
            .slice(1)
            .match(/.{2}/g)
            .map((channel) => Number.parseInt(channel, 16) / 255);
    }
    const match = /^rgb\(([\d.]+)%?,\s*([\d.]+)%?,\s*([\d.]+)%?\)$/.exec(color);
    if (!match) {
        throw new Error(`Unsupported CSS color: ${color}`);
    }
    const percentage = color.includes('%');
    return match.slice(1).map((channel) => Number.parseFloat(channel) / (percentage ? 100 : 255));
}

function mixColors(first, second, firstWeight) {
    return cssColorChannels(first).map((channel, index) => channel * firstWeight + cssColorChannels(second)[index] * (1 - firstWeight));
}

function relativeLuminanceChannels(channels) {
    const linear = channels.map((value) => {
        return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * linear[0] + 0.7152 * linear[1] + 0.0722 * linear[2];
}

function contrastRatio(first, second) {
    const firstLuminance = Array.isArray(first) ? relativeLuminanceChannels(first) : relativeLuminance(first);
    const secondLuminance = Array.isArray(second) ? relativeLuminanceChannels(second) : relativeLuminance(second);
    const lighter = Math.max(firstLuminance, secondLuminance);
    const darker = Math.min(firstLuminance, secondLuminance);
    return (lighter + 0.05) / (darker + 0.05);
}

function compiledThemeProperties(file) {
    const css = compile(resolve(repoRoot, file), {
        loadPaths: [repoRoot, resolve(repoRoot, 'node_modules')],
        quietDeps: true,
        silenceDeprecations: ['color-functions', 'global-builtin', 'if-function', 'import'],
    }).css;
    const properties = {};
    postcss.parse(css).walkDecls(/^--(?:artemis-alert|module-bg)/, (declaration) => {
        properties[declaration.prop] = declaration.value;
    });
    return properties;
}

function dependencyCatalog(specifier) {
    const name = specifier.slice('catalog:'.length);
    return name ? namedCatalogs[name] : catalog;
}

const themeColorUtility = /^(?:bg|text|border(?:-[trblxy])?|outline|ring(?:-offset)?)-(tum-ui-[\w-]+)$/;

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

describe('@tumaet/ui-angular package manifest', () => {
    it('pins every dependency to the root pnpm catalog version', () => {
        const dependencies = { ...packageJson.peerDependencies, ...packageJson.dependencies };
        const rootDependencies = { ...rootPackageJson.dependencies, ...rootPackageJson.devDependencies };

        expect(rootPackageJson.dependencies['@tumaet/ui-angular']).toBe('workspace:*');
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
    });

    it('keeps the package private and declares its stylesheet subpath', () => {
        expect(packageJson.private).toBe(true);
        expect(packageJson.files).toEqual(expect.arrayContaining(['fesm2022', 'types', 'styles.css', 'themes.css', 'README.md', 'LICENSE']));
        expect(packageJson.sideEffects).toEqual(expect.arrayContaining(['./styles.css', './themes.css']));
        expect(packageJson.exports).toMatchObject({ './styles.css': './styles.css', './themes.css': './themes.css' });
        expect(productionBuild.assets).toContain('styles.css');
        expect(productionBuild.assets).toContain('themes.css');
    });

    it('keeps production on the built entry point and development on the public source entry point', () => {
        expect(rootTsconfig.compilerOptions.paths['@tumaet/ui-angular']).toEqual(['./packages/tum-ui/dist']);
        expect(rootTsconfig.compilerOptions.paths['@tumaet/ui-angular/*']).toBeUndefined();
        expect(serveTsconfig.compilerOptions.paths['@tumaet/ui-angular']).toEqual(['./packages/tum-ui/src/public-api.ts']);
        expect(angularWorkspace.projects.artemis.architect.build.configurations['tum-ui-source'].tsConfig).toBe('tsconfig.serve.json');
        expect(angularWorkspace.projects.artemis.architect.serve.options.buildTarget).toBe('artemis:build:development,tum-ui-source');
        expect(angularWorkspace.projects.artemis.architect.serve.options.prebundle.exclude).toContain('@tumaet/ui-angular');
        expect(publicApi).not.toMatch(/export\s+\*\s+from/);
    });

    it('loads the validated package stylesheet after host framework styles', () => {
        const styles = angularWorkspace.projects.artemis.architect.build.options.styles;
        const packageStylesheet = '@tumaet/ui-angular/styles.css';

        expect(styles.filter((style) => style === packageStylesheet)).toHaveLength(1);
        expect(styles.indexOf(packageStylesheet)).toBeGreaterThan(styles.indexOf('src/main/webapp/content/scss/themes/theme-default.scss'));
        expect(styles.indexOf(packageStylesheet)).toBeGreaterThan(styles.indexOf('src/main/webapp/tailwind.css'));
    });

    it('keeps Tailwind generation scoped to package runtime sources', () => {
        expect(packageTailwind).toContain('prefix(tum)');
        expect(packageTailwind).toContain('source(none)');
        expect(packageTailwind).toContain("@source './src'");
        expect(packageTailwind).toContain("@source not './src/**/*.spec.ts'");
        expect(packageTailwind).toContain("@source not './src/**/*.stories.ts'");
    });

    it('prefixes every static template class', () => {
        const invalidClasses = packageTemplates.flatMap(({ file, source }) =>
            [...source.matchAll(/\bclass="([^"]*)"/g)]
                .flatMap((match) => match[1].split(/\s+/))
                .filter((className) => className && !className.startsWith('tum:') && !className.startsWith('tum-ui-'))
                .map((className) => `${file}: ${className}`),
        );

        expect(invalidClasses).toEqual([]);
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
        const primitiveHostTokens = [];
        const hostTokens = {};
        postcss.parse(hostTheme).walkDecls(/^--tum-ui-/, (declaration) => {
            hostTokens[declaration.prop] = declaration.value;
            if (/var\(--p-(?:surface|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)-\d+\b/.test(declaration.value)) {
                primitiveHostTokens.push(`${declaration.prop}: ${declaration.value}`);
            }
        });

        expect(consumedProperties.filter((property) => !property.startsWith('--tum-ui-'))).toEqual([]);
        expect(packageProperties.filter((property) => property.startsWith('--tum-ui-surface-'))).toEqual([]);
        expect(packageRuntimeSources).not.toContain('tum:dark:');
        expect(packageRuntimeSources).not.toContain(":host-context(html[data-theme='dark'])");
        expect(hostProperties).toEqual(packageProperties);
        expect(storybookProperties).toEqual(packageProperties);
        expect(utilityColors.filter((color) => !themeColors.has(color))).toEqual([]);
        expect(hostColorUtilities).toEqual([]);
        expect(primitiveHostTokens).toEqual([]);
        for (const state of ['danger', 'success', 'warning', 'info']) {
            expect(hostTokens[`--tum-ui-state-${state}`]).toBe(`var(--artemis-alert-${state}-color)`);
            expect(hostTokens[`--tum-ui-state-${state}-contrast`]).toBe(`var(--artemis-alert-${state}-background)`);
            expect(hostTokens[`--tum-ui-state-${state}-foreground`]).toBe(`var(--artemis-alert-${state}-color)`);
        }
    });

    it('keeps Artemis state colors readable in both themes', () => {
        const themes = {
            light: compiledThemeProperties('src/main/webapp/content/scss/themes/theme-default.scss'),
            dark: compiledThemeProperties('src/main/webapp/content/scss/themes/theme-dark.scss'),
        };

        for (const [theme, properties] of Object.entries(themes)) {
            for (const state of ['danger', 'success', 'warning', 'info']) {
                const color = properties[`--artemis-alert-${state}-color`];
                const contrast = properties[`--artemis-alert-${state}-background`];
                const tagBackground = mixColors(color, properties['--module-bg'], 0.2);
                expect(contrastRatio(color, contrast), `${theme}: filled ${state}`).toBeGreaterThanOrEqual(4.5);
                expect(contrastRatio(color, tagBackground), `${theme}: tinted ${state}`).toBeGreaterThanOrEqual(4.5);
            }
        }
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
