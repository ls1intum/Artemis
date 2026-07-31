import { globSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';

const packageRoot = dirname(fileURLToPath(import.meta.url));
const packageDirectoryArgument = process.argv.indexOf('--package-dir');
const packageDirectory = packageDirectoryArgument >= 0 ? resolve(process.argv[packageDirectoryArgument + 1]) : resolve(packageRoot, 'dist');
const stylesheetPath = resolve(packageDirectory, 'styles.css');
const manifestPath = resolve(packageDirectory, 'package.json');
const runtimePaths = globSync(resolve(packageRoot, 'src/**/*.{html,scss,ts}')).filter((path) => !path.endsWith('.spec.ts') && !path.endsWith('.stories.ts'));
const artifactRuntimePaths = globSync(resolve(packageDirectory, 'fesm2022/**/*.mjs'));
const [css, manifestSource, ...sources] = await Promise.all([
    readFile(stylesheetPath, 'utf8'),
    readFile(manifestPath, 'utf8'),
    ...runtimePaths.map((path) => readFile(path, 'utf8')),
    ...artifactRuntimePaths.map((path) => readFile(path, 'utf8')),
]);
const runtimeSources = sources.slice(0, runtimePaths.length);
const artifactRuntimeSources = sources.slice(runtimePaths.length);
const stylesheet = postcss.parse(css, { from: stylesheetPath });
const manifest = JSON.parse(manifestSource);
const errors = [];

if (css.includes('--tw-')) {
    errors.push('an unnamespaced Tailwind custom property remains');
}
if (css.includes('--artemis-')) {
    errors.push('the package stylesheet references an Artemis-owned custom property');
}
if (runtimeSources.some((source) => source.includes('--artemis-')) || artifactRuntimeSources.some((source) => source.includes('--artemis-'))) {
    errors.push('the package runtime references an Artemis-owned custom property');
}

const forbiddenAtRules = new Set(['custom-variant', 'import', 'source', 'theme']);
stylesheet.walkAtRules((atRule) => {
    if (forbiddenAtRules.has(atRule.name)) {
        errors.push(`the compiled stylesheet contains @${atRule.name}`);
    }
    if (atRule.name === 'layer' && atRule.params !== 'properties') {
        errors.push(`the compiled stylesheet contains unexpected @layer ${atRule.params}`);
    }
    if (atRule.name.endsWith('keyframes') && !atRule.params.startsWith('tum-')) {
        errors.push(`the compiled stylesheet contains unnamespaced keyframes ${atRule.params}`);
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

const contrastRule = ruleFor('tum:text-primary-contrast');
if (declarationValue(contrastRule, 'color') !== 'var(--tumaet-ui-primary-contrast-color)') {
    errors.push('the primary contrast selector does not use its package token');
}
const controlBackgroundRule = ruleFor('tum:bg-control-background');
if (declarationValue(controlBackgroundRule, 'background-color') !== 'var(--tumaet-ui-control-background)') {
    errors.push('the control background selector does not use its semantic package token');
}
const buttonFontRule = ruleFor('tum-ui-btn');
if (
    declarationValue(buttonFontRule, 'font-family') !== 'var(--tumaet-ui-font-family)' ||
    declarationValue(buttonFontRule, 'font-size') !== 'var(--tumaet-ui-font-size-base)' ||
    declarationValue(buttonFontRule, 'line-height') !== 'var(--tumaet-ui-line-height-base)'
) {
    errors.push('package hosts do not use the package typography contract');
}
let nativeControlsInheritTypography = false;
stylesheet.walkDecls('font', (declaration) => {
    if (declaration.value === 'inherit' && declaration.parent.selector.includes('tum-ui-')) {
        nativeControlsInheritTypography = true;
    }
});
if (!nativeControlsInheritTypography) {
    errors.push('native package controls do not inherit package typography');
}
const focusRule = ruleFor('tum:focus-visible:outline-focus');
if (declarationValue(focusRule, 'outline-color') !== 'var(--tumaet-ui-focus-color)') {
    errors.push('the focus selector does not use its semantic package token');
}
if (css.includes('--tumaet-ui-surface-')) {
    errors.push('the compiled stylesheet exposes a primitive surface token');
}
if ([...compiledClasses].some((className) => className.startsWith('tum:dark:'))) {
    errors.push('the compiled stylesheet contains a theme-specific utility');
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
    } else if (selectorClasses.length === 0 && !rule.selector.includes('tum-ui-')) {
        errors.push(`unexpected element selector ${rule.selector}`);
    }
});

const keyframes = new Set();
stylesheet.walkAtRules((atRule) => {
    if (atRule.name.endsWith('keyframes')) {
        keyframes.add(atRule.params);
    }
});
stylesheet.walkDecls(/^--tum-animate-/, (declaration) => {
    const name = declaration.value.split(/\s+/, 1)[0];
    if (!name.startsWith('tum-') || !keyframes.has(name)) {
        errors.push(`the animation token ${declaration.prop} references invalid keyframes ${name}`);
    }
});
if (
    manifest.exports?.['./styles.css'] !== './styles.css' ||
    manifest.exports?.['./themes.css'] !== './themes.css' ||
    manifest.exports?.['./tailwind-theme.css'] ||
    JSON.stringify(manifest.sideEffects) !== '["./styles.css","./themes.css"]'
) {
    errors.push('the built manifest does not expose the stylesheet and theme contracts');
}
if (manifest.peerDependencies?.tailwindcss) {
    errors.push('the built manifest exposes build-only Tailwind as a consumer peer');
}

if (errors.length > 0) {
    throw new Error(`Invalid TUM UI stylesheet:\n- ${[...new Set(errors)].join('\n- ')}`);
}

console.log(`Validated ${stylesheetPath}`);
