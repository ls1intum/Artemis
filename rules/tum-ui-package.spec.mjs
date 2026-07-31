import { globSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';
import { compile } from 'sass';
import semver from 'semver';
import ts from 'typescript';
import { parseTemplate, TmplAstRecursiveVisitor, tmplAstVisitAll } from '@angular/compiler';
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
const artemisTranslatorSource = readFileSync(resolve(repoRoot, 'src/main/webapp/app/shared-ui/tum-ui-integration/artemis-tum-ui-translator.ts'), 'utf8');
const packageTailwind = readFileSync(resolve(repoRoot, 'packages/tum-ui/tailwind.css'), 'utf8');
const packageTailwindTheme = readFileSync(resolve(repoRoot, 'packages/tum-ui/tailwind-theme.css'), 'utf8');
const packageRuntimeSources = globSync(['packages/tum-ui/src/**/*.{html,scss,ts}'], { cwd: repoRoot })
    .filter((file) => !file.endsWith('.spec.ts') && !file.endsWith('.stories.ts'))
    .map((file) => readFileSync(resolve(repoRoot, file), 'utf8'))
    .join('\n');
const packageTemplates = globSync('packages/tum-ui/src/**/*.html', { cwd: repoRoot }).map((file) => ({
    file,
    source: readFileSync(resolve(repoRoot, file), 'utf8'),
}));
const artemisExternalTemplates = globSync('src/main/webapp/**/*.html', { cwd: repoRoot }).map((file) => ({
    file,
    lineOffset: 0,
    source: readFileSync(resolve(repoRoot, file), 'utf8'),
}));
const packageStyles = globSync(['packages/tum-ui/src/**/*.{css,scss}', 'packages/tum-ui/tailwind.css', 'packages/tum-ui/tailwind-theme.css', 'packages/tum-ui/themes.css'], {
    cwd: repoRoot,
})
    .map((file) => readFileSync(resolve(repoRoot, file), 'utf8'))
    .join('\n');
const storybookTheme = readFileSync(resolve(repoRoot, 'packages/tum-ui/themes.css'), 'utf8');
const hostTailwind = readFileSync(resolve(repoRoot, 'src/main/webapp/tailwind.css'), 'utf8');
const workspace = parse(readFileSync(resolve(repoRoot, 'pnpm-workspace.yaml'), 'utf8'));
const catalog = workspace.catalog ?? {};
const namedCatalogs = workspace.catalogs ?? {};

function customProperties(marker) {
    const start = storybookTheme.indexOf(marker);
    const openingBrace = storybookTheme.indexOf('{', start);
    const closingBrace = storybookTheme.indexOf('}', openingBrace);
    return Object.fromEntries(
        [...storybookTheme.slice(openingBrace + 1, closingBrace).matchAll(/(--[\w-]+):\s*([^;]+);/g)]
            .map((match) => [match[1], [...match[2].matchAll(/#[\da-f]{3}(?:[\da-f]{3})?/gi)].at(-1)?.[0]])
            .filter((entry) => entry[1]),
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

function stringRecord(source, variableName) {
    const sourceFile = ts.createSourceFile('source.ts', source, ts.ScriptTarget.Latest, true);
    const declaration = sourceFile.statements
        .filter(ts.isVariableStatement)
        .flatMap((statement) => [...statement.declarationList.declarations])
        .find((candidate) => ts.isIdentifier(candidate.name) && candidate.name.text === variableName);
    let initializer = declaration?.initializer;
    while (initializer && (ts.isAsExpression(initializer) || ts.isSatisfiesExpression(initializer) || ts.isParenthesizedExpression(initializer))) {
        initializer = initializer.expression;
    }
    if (!initializer || !ts.isObjectLiteralExpression(initializer)) {
        throw new Error(`${variableName} must be an object literal`);
    }
    return Object.fromEntries(
        initializer.properties.map((property) => {
            if (!ts.isPropertyAssignment(property) || (!ts.isStringLiteral(property.name) && !ts.isIdentifier(property.name)) || !ts.isStringLiteral(property.initializer)) {
                throw new Error(`${variableName} must contain only string literal entries`);
            }
            return [property.name.text, property.initializer.text];
        }),
    );
}

function hasTranslation(catalog, key) {
    const translation = key.split('.').reduce((value, segment) => value?.[segment], catalog);
    return typeof translation === 'string';
}

function inlineTemplates(file, source) {
    const sourceFile = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true);
    const templates = [];

    function visit(node) {
        if (ts.isPropertyAssignment(node) && (ts.isIdentifier(node.name) || ts.isStringLiteral(node.name)) && node.name.text === 'template') {
            if (!ts.isStringLiteralLike(node.initializer)) {
                throw new Error(`${file}: inline Angular templates must be static string literals`);
            }
            templates.push({
                file,
                lineOffset: sourceFile.getLineAndCharacterOfPosition(node.initializer.getStart(sourceFile)).line,
                source: node.initializer.text,
            });
        }
        ts.forEachChild(node, visit);
    }

    visit(sourceFile);
    return templates;
}

const artemisInlineTemplates = globSync('src/main/webapp/**/*.ts', { cwd: repoRoot })
    .filter((file) => !file.endsWith('.spec.ts'))
    .flatMap((file) => {
        const source = readFileSync(resolve(repoRoot, file), 'utf8');
        return source.includes('<tum-ui-') ? inlineTemplates(file, source) : [];
    });
const artemisTemplates = [...artemisExternalTemplates, ...artemisInlineTemplates].filter(({ source }) => source.includes('<tum-ui-'));

const removedOutputBindings = new Set(['completeMethod', 'onSelect', 'onUnselect', 'onChange', 'onRemove', 'parseValidChange', 'onShow', 'onHide', 'onClick']);

class RemovedOutputBindingVisitor extends TmplAstRecursiveVisitor {
    constructor(file, lineOffset, violations) {
        super();
        this.file = file;
        this.lineOffset = lineOffset;
        this.violations = violations;
    }

    visitElement(element) {
        if (element.name.startsWith('tum-ui-')) {
            for (const output of element.outputs.filter(({ name }) => removedOutputBindings.has(name))) {
                this.violations.push(`${this.file}:${this.lineOffset + output.sourceSpan.start.line + 1}: ${element.name} (${output.name})`);
            }
        }
        return super.visitElement(element);
    }
}

function removedOutputBindingViolations(templates) {
    const violations = [];
    for (const { file, lineOffset, source } of templates) {
        const parsed = parseTemplate(source, file);
        if (parsed.errors?.length) {
            throw new Error(parsed.errors.map((error) => `${file}: ${error}`).join('\n'));
        }
        tmplAstVisitAll(new RemovedOutputBindingVisitor(file, lineOffset, violations), parsed.nodes);
    }
    return violations;
}

describe('@tumaet/ui-angular integration contract', () => {
    it('rejects removed package output bindings in Artemis templates', () => {
        expect(removedOutputBindingViolations(artemisTemplates)).toEqual([]);
        expect(removedOutputBindingViolations([{ file: 'fixture.html', lineOffset: 0, source: '<tum-ui-checkbox (onChange)="save()" />' }])).toEqual([
            'fixture.html:1: tum-ui-checkbox (onChange)',
        ]);
    });

    it('uses the catalog as the installed version and keeps it within every peer range', () => {
        const rootDependencies = { ...rootPackageJson.dependencies, ...rootPackageJson.devDependencies };

        expect(rootPackageJson.dependencies['@tumaet/ui-angular']).toBe('workspace:*');
        expect(Object.keys(catalog).length).toBeGreaterThan(0);
        for (const [name, version] of Object.entries(packageJson.dependencies)) {
            expect(catalog[name], `${name} must have one canonical workspace version`).toBe(version);
            expect(rootDependencies[name], `${name} must be shared with Artemis through the catalog`).toBe('catalog:');
            expect(version, `${name} must be valid in the ng-packagr output`).not.toMatch(/^(?:catalog|workspace):/);
        }
        for (const [name, range] of Object.entries(packageJson.peerDependencies)) {
            expect(semver.validRange(range), `${name} must declare a valid compatibility range`).not.toBeNull();
            expect(semver.satisfies(catalog[name], range), `${name} catalog version must satisfy ${range}`).toBe(true);
            expect(rootDependencies[name], `${name} must be shared with Artemis through the catalog`).toBe('catalog:');
            expect(packageJson.devDependencies[name], `${name} must be installed for isolated package development`).toBe('catalog:');
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
        expect(packageJson.files).not.toContain('tailwind-theme.css');
        expect(packageJson.sideEffects).toEqual(['./styles.css', './themes.css']);
        expect(packageJson.exports).toMatchObject({
            './styles.css': './styles.css',
            './themes.css': './themes.css',
        });
        expect(packageJson.exports).not.toHaveProperty('./tailwind-theme.css');
        expect(productionBuild.assets).toContain('styles.css');
        expect(productionBuild.assets).toContain('themes.css');
        expect(productionBuild.assets).not.toContain('tailwind-theme.css');
    });

    it('keeps production on the built entry point and development on the public source entry point', () => {
        expect(rootTsconfig.compilerOptions.paths['@tumaet/ui-angular']).toEqual(['./packages/tum-ui/dist']);
        expect(rootTsconfig.compilerOptions.paths['@tumaet/ui-angular/*']).toBeUndefined();
        expect(serveTsconfig.compilerOptions.paths['@tumaet/ui-angular']).toEqual(['./packages/tum-ui/src/public-api.ts']);
        expect(angularWorkspace.projects.artemis.architect.build.configurations['tum-ui-source'].tsConfig).toBe('tsconfig.serve.json');
        expect(angularWorkspace.projects.artemis.architect.serve.options.buildTarget).toBe('artemis:build:development,tum-ui-source');
        expect(angularWorkspace.projects.artemis.architect.serve.options.prebundle).toBe(false);
        expect(publicApi).not.toMatch(/export\s+\*\s+from/);
    });

    it('loads the validated package stylesheet after host framework styles', () => {
        const styles = angularWorkspace.projects.artemis.architect.build.options.styles;
        const packageStylesheet = '@tumaet/ui-angular/styles.css';
        const packageTheme = '@tumaet/ui-angular/themes.css';

        expect(styles.filter((style) => style === packageStylesheet)).toHaveLength(1);
        expect(styles).not.toContain(packageTheme);
        expect(styles.indexOf(packageStylesheet)).toBeGreaterThan(styles.indexOf('src/main/webapp/content/scss/themes/theme-default.scss'));
        expect(styles.indexOf(packageStylesheet)).toBeGreaterThan(styles.indexOf('src/main/webapp/tailwind.css'));
    });

    it('keeps Tailwind generation scoped to package runtime sources', () => {
        expect(packageTailwind).toContain('prefix(tum)');
        expect(packageTailwind).toContain('source(none)');
        expect(packageTailwind).toContain("@source './src'");
        expect(packageTailwind).toContain("@source not './src/**/*.spec.ts'");
        expect(packageTailwind).toContain("@source not './src/**/*.stories.ts'");
        expect(packageTailwindTheme).toMatch(/--breakpoint-sm:\s*40rem/);
        expect(packageTailwindTheme).toMatch(/--breakpoint-md:\s*48rem/);
        expect(packageTailwindTheme).toMatch(/--breakpoint-lg:\s*64rem/);
        expect(packageTailwindTheme).toMatch(/--breakpoint-xl:\s*80rem/);
        expect(packageTailwindTheme).toMatch(/--breakpoint-2xl:\s*96rem/);
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
        const consumedProperties = [
            ...new Set([...`${packageRuntimeSources}\n${packageTailwind}\n${packageTailwindTheme}`.matchAll(/var\((--tumaet-ui-[\w-]+)/g)].map((match) => match[1])),
        ].sort();
        const packageProperties = [...new Set([...packageStyles.matchAll(/--tumaet-ui-[\w-]+/g)].map((match) => match[0]))].sort();
        const referenceProperties = [...new Set([...storybookTheme.matchAll(/--tumaet-ui-[\w-]+/g)].map((match) => match[0]))].sort();
        const tailwindProperties = [...new Set([...packageTailwindTheme.matchAll(/var\((--tumaet-ui-[\w-]+)/g)].map((match) => match[1]))].sort();
        const hostPropertyNames = [];
        const hostTokens = {};
        postcss.parse(hostTailwind).walkDecls(/^--tumaet-ui-/, (declaration) => {
            hostPropertyNames.push(declaration.prop);
            if (declaration.prop.startsWith('--tumaet-ui-state-')) {
                hostTokens[declaration.prop] = declaration.value;
            }
        });
        const hostProperties = [...new Set(hostPropertyNames)].sort();

        expect(packageStyles).not.toContain('--artemis-');
        expect(packageRuntimeSources).not.toContain('tum:dark:');
        expect(packageRuntimeSources).not.toContain(":host-context(html[data-theme='dark'])");
        expect(consumedProperties).toEqual(packageProperties);
        expect(referenceProperties).toEqual(packageProperties);
        expect(hostProperties).toEqual(packageProperties);
        expect(tailwindProperties.every((property) => packageProperties.includes(property))).toBe(true);
        expect(hostTailwind).not.toContain('packages/tum-ui/tailwind-theme.css');
        for (const state of ['danger', 'success', 'warning', 'info']) {
            expect(hostTokens[`--tumaet-ui-state-${state}`]).toContain(`var(--artemis-alert-${state}-color`);
            expect(hostTokens[`--tumaet-ui-state-${state}-contrast`]).toContain(`var(--artemis-alert-${state}-background`);
            expect(hostTokens[`--tumaet-ui-state-${state}-foreground`]).toContain(`var(--artemis-alert-${state}-color`);
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

    it('maps every package translation to an existing Artemis translation in each locale', () => {
        const mappings = stringRecord(artemisTranslatorSource, 'ARTEMIS_TRANSLATION_KEYS');
        const missing = ['en', 'de'].flatMap((locale) => {
            const catalogs = globSync(`src/main/webapp/i18n/${locale}/*.json`, { cwd: repoRoot }).map((file) => JSON.parse(readFileSync(resolve(repoRoot, file), 'utf8')));
            return Object.entries(mappings)
                .filter(([, target]) => !catalogs.some((catalog) => hasTranslation(catalog, target)))
                .map(([source, target]) => `${locale}: ${source} -> ${target}`);
        });

        expect(missing).toEqual([]);
    });

    it('keeps the reference themes readable', () => {
        const themes = {
            light: customProperties("[data-theme='light']"),
            dark: customProperties("[data-theme='dark']"),
        };
        const pairs = [
            ['--tumaet-ui-primary-contrast-color', '--tumaet-ui-primary-color', 4.5],
            ['--tumaet-ui-accent-color', '--tumaet-ui-content-background', 4.5],
            ['--tumaet-ui-text-color', '--tumaet-ui-content-background', 4.5],
            ['--tumaet-ui-muted-color', '--tumaet-ui-content-background', 4.5],
            ['--tumaet-ui-muted-color', '--tumaet-ui-control-background', 4.5],
            ['--tumaet-ui-highlight-color', '--tumaet-ui-highlight-background', 4.5],
            ['--tumaet-ui-contrast-color', '--tumaet-ui-contrast-background', 4.5],
            ['--tumaet-ui-tooltip-color', '--tumaet-ui-tooltip-background', 4.5],
            ['--tumaet-ui-control-border-color', '--tumaet-ui-control-background', 3],
            ['--tumaet-ui-control-border-hover-color', '--tumaet-ui-control-background', 3],
            ['--tumaet-ui-focus-color', '--tumaet-ui-content-background', 3],
            ['--tumaet-ui-focus-color', '--tumaet-ui-control-background', 3],
            ['--tumaet-ui-focus-color', '--tumaet-ui-overlay-background', 3],
            ...['danger', 'success', 'warning', 'info'].map((state) => [`--tumaet-ui-state-${state}-contrast`, `--tumaet-ui-state-${state}`, 4.5]),
        ];

        for (const [theme, properties] of Object.entries(themes)) {
            for (const [foreground, background, minimum] of pairs) {
                const actual = contrastRatio(properties[foreground], properties[background]);
                expect(actual, `${theme}: ${foreground} on ${background}`).toBeGreaterThanOrEqual(minimum);
            }
        }
    });
});
