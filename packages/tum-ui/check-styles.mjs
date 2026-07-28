import { globSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';

const packageRoot = dirname(fileURLToPath(import.meta.url));
const packageDirectoryArgument = process.argv.indexOf('--package-dir');
const packageDirectory = packageDirectoryArgument >= 0 ? resolve(process.argv[packageDirectoryArgument + 1]) : resolve(packageRoot, '../../dist/tum-ui');
const stylesheetPath = resolve(packageDirectory, 'styles.css');
const manifestPath = resolve(packageDirectory, 'package.json');
const runtimePaths = globSync(resolve(packageRoot, 'src/**/*.{html,scss,ts}')).filter((path) => !path.endsWith('.spec.ts'));
const [css, manifestSource, ...runtimeSources] = await Promise.all([
    readFile(stylesheetPath, 'utf8'),
    readFile(manifestPath, 'utf8'),
    ...runtimePaths.map((path) => readFile(path, 'utf8')),
]);
const stylesheet = postcss.parse(css, { from: stylesheetPath });
const manifest = JSON.parse(manifestSource);
const errors = [];

if (css.includes('--tw-')) {
    errors.push('an unnamespaced Tailwind custom property remains');
}

const forbiddenAtRules = new Set(['custom-variant', 'import', 'layer', 'source', 'theme']);
stylesheet.walkAtRules((atRule) => {
    if (forbiddenAtRules.has(atRule.name)) {
        errors.push(`the compiled stylesheet contains @${atRule.name}`);
    }
});

function classNames(rule) {
    return [...rule.selector.matchAll(/(?<!\\)\.((?:\\.|[\w-])*)/g)].map((match) => match[1].replaceAll('\\', ''));
}

const compiledClasses = new Set();
stylesheet.walkRules((rule) => classNames(rule).forEach((className) => compiledClasses.add(className)));
const runtimeClasses = new Set(runtimeSources.flatMap((source) => [...source.matchAll(/tum:[^\s"'`$]+/g)].map((match) => match[0])));
for (const className of runtimeClasses) {
    if (!compiledClasses.has(className)) {
        errors.push(`runtime utility .${className} is missing`);
    }
}

function ruleFor(className) {
    let result;
    stylesheet.walkRules((rule) => {
        if (!result && classNames(rule).includes(className)) {
            result = rule;
        }
    });
    return result;
}

function declarationValue(rule, property) {
    return rule?.nodes.find((node) => node.type === 'decl' && node.prop === property)?.value;
}

const contrastRule = ruleFor('tum:text-tum-ui-primary-contrast');
if (declarationValue(contrastRule, 'color') !== 'var(--tum-ui-primary-contrast)') {
    errors.push('the primary contrast selector does not use its package token');
}
const darkSurfaceRule = ruleFor('tum:dark:bg-tum-ui-surface-900');
const normalizedDarkSelector = darkSurfaceRule?.selector.replaceAll("'", '').replaceAll('"', '').replaceAll(/\s+/g, '');
if (!normalizedDarkSelector?.includes(':where([data-theme=dark],[data-theme=dark]*)')) {
    errors.push('the dark surface selector is not guarded by the package theme contract');
}
if (declarationValue(darkSurfaceRule, 'background-color') !== 'var(--tum-ui-surface-900)') {
    errors.push('the dark surface selector does not use its package token');
}

stylesheet.walkRules((rule) => {
    if (rule.parent.type === 'atrule' && rule.parent.name.endsWith('keyframes')) {
        return;
    }

    const selectorClasses = classNames(rule);
    for (const className of selectorClasses) {
        if (!className.startsWith('tum:') && !className.startsWith('tum-ui-')) {
            errors.push(`unscoped class selector .${className}`);
        }
    }

    const globalSelector = rule.selector
        .split(',')
        .map((selector) => selector.trim())
        .every((selector) => ['*', ':before', ':after', '::before', '::after', '::backdrop', ':root', ':host'].includes(selector));
    if (globalSelector) {
        rule.walkDecls((declaration) => {
            if (!declaration.prop.startsWith('--tum-')) {
                errors.push(`global selector ${rule.selector} sets ${declaration.prop}`);
            }
        });
    } else if (selectorClasses.length === 0) {
        errors.push(`unexpected element selector ${rule.selector}`);
    }
});

let spinToken;
stylesheet.walkDecls('--tum-animate-spin', (declaration) => {
    spinToken = declaration;
});
const spinKeyframes = stylesheet.nodes.find((node) => node.type === 'atrule' && node.name.endsWith('keyframes') && node.params === 'tum-spin');
if (!spinToken?.value.startsWith('tum-spin ') || !spinKeyframes) {
    errors.push('the namespaced spin animation contract is incomplete');
}
if (manifest.exports?.['./styles.css'] !== './styles.css' || JSON.stringify(manifest.sideEffects) !== '["./styles.css"]') {
    errors.push('the built manifest does not expose the stylesheet contract');
}
if (manifest.peerDependencies?.tailwindcss) {
    errors.push('the built manifest exposes build-only Tailwind as a consumer peer');
}

if (errors.length > 0) {
    throw new Error(`Invalid TUM UI stylesheet:\n- ${[...new Set(errors)].join('\n- ')}`);
}

console.log(`Validated ${stylesheetPath}`);
