// Reads what a project's Tailwind theme declares: the --color-* tokens
// inside @theme, the scales, and the classes its CSS defines.
// See ../../README.md for the native port and its analysis boundaries.
import * as fs from 'node:fs';
import * as path from 'node:path';
import postcss from 'postcss';
import selectorParser from 'postcss-selector-parser';
import valueParser from 'postcss-value-parser';
import { normalizeClass, OPACITY_MODIFIER } from '../grammar/classes.mjs';
import { parseColor } from '../grammar/colors.mjs';
import { lengthInPx } from '../grammar/lengths.mjs';
import { FONT_SIZES, RADII } from '../grammar/tailwind-theme.mjs';
import { projectFor } from './configuration.mjs';
import { isFile, mtimeOf, TTL } from './fs.mjs';
import { resolveStylesheet } from '../tailwind/oracle.mjs';
const cache = new Map();
function stylesheet(css) {
    return typeof css === 'string' ? postcss.parse(css) : css;
}
export function parseColorTokens(css) {
    const tokens = new Set();
    applyColorTokens(css, tokens);
    return tokens;
}
function applyColorTokens(css, tokens) {
    applyTokenDeclarations(parseDeclarations(css).declarations, tokens);
}
// The namespaces Tailwind reads a color utility from before --color-*,
// verified against Tailwind 4.3.3. Shadows, inset rings and gradient
// stops read --color-* only.
export const COLOR_NAMESPACES = [
    'background-color',
    'text-color',
    'border-color',
    'divide-color',
    'ring-color',
    'outline-color',
    'accent-color',
    'caret-color',
    'placeholder-color',
    'text-decoration-color',
    'text-shadow-color',
    'drop-shadow-color',
    'fill',
    'stroke',
];
// In cascade order: `--color-x: initial` drops x, `--color-*: initial`
// and `--*: initial` drop everything declared so far. A scoped
// namespace resets on its own.
function applyTokenDeclarations(declarations, tokens, scoped) {
    for (const { name, value, theme } of declarations) {
        if (!theme) continue;
        const reset = value.trim() === 'initial';
        if (name === '*') {
            if (reset) {
                tokens.clear();
                scoped?.clear();
            }
            continue;
        }
        const namespace = name.startsWith('color-') ? 'color' : COLOR_NAMESPACES.find((candidate) => name.startsWith(`${candidate}-`));
        if (!namespace) continue;
        const token = name.slice(namespace.length + 1);
        let set = tokens;
        if (namespace !== 'color') {
            if (!scoped) continue;
            set = scoped.get(namespace) ?? new Set();
            scoped.set(namespace, set);
        }
        if (token === '*') {
            if (reset) set.clear();
        } else if (reset) set.delete(token);
        else set.add(token);
    }
}
const DARK_PRELUDE = /\.dark(?![\w-])|prefers-color-scheme\s*:\s*dark|data-(?:theme|mode)=["']?dark|@variant\s+dark\b/;
// Dark-mode blocks are skipped, so the values are the light theme's.
// Later declarations win, as in CSS.
export function parseDeclarations(css) {
    const values = new Map();
    const themeNames = new Set();
    const declarations = [];
    stylesheet(css).walkDecls((declaration) => {
        if (!declaration.prop.startsWith('--')) return;
        let theme = false;
        for (let parent = declaration.parent; parent; parent = parent.parent) {
            const prelude = parent.type === 'rule' ? parent.selector : parent.type === 'atrule' ? `@${parent.name} ${parent.params}` : '';
            if (DARK_PRELUDE.test(prelude)) return;
            if (parent.type === 'atrule' && parent.name === 'theme') theme = true;
        }
        const name = declaration.prop.slice(2);
        const value = declaration.value;
        values.set(name, value);
        if (theme) themeNames.add(name);
        declarations.push({ name, value, theme });
    });
    return { values, themeNames, declarations };
}
// Null when a variable has no value and no fallback.
export function resolveVariables(value, values, depth = 0) {
    if (depth > 6) return null;
    let failed = false;
    const parsed = valueParser(value);
    parsed.walk((node) => {
        if (node.type !== 'function') return;
        if (node.value === 'url') return false;
        if (node.value !== 'var') return;
        const name = node.nodes.find((part) => part.type !== 'space' && part.type !== 'comment');
        const comma = node.nodes.findIndex((part) => part.type === 'div' && part.value === ',');
        const fallback = comma === -1 ? undefined : valueParser.stringify(node.nodes.slice(comma + 1));
        const inner = name?.type === 'word' && name.value.startsWith('--') ? (values.get(name.value.slice(2)) ?? fallback) : undefined;
        const resolved = inner === undefined ? null : resolveVariables(inner.trim(), values, depth + 1);
        if (resolved === null) failed = true;
        node.type = 'word';
        node.value = resolved ?? '';
        delete node.nodes;
        return false;
    });
    return failed ? null : parsed.toString();
}
export function parseImports(css) {
    const out = [];
    stylesheet(css).walkAtRules('import', (rule) => {
        const first = valueParser(rule.params).nodes.find((node) => node.type !== 'space' && node.type !== 'comment');
        const source = first?.type === 'function' && first.value === 'url' ? first.nodes.find((node) => node.type !== 'space' && node.type !== 'comment') : first;
        if (source?.type === 'string' || source?.type === 'word') out.push(source.value);
    });
    return out;
}
export function parseUtilities(css) {
    const out = new Set();
    stylesheet(css).walkAtRules('utility', (rule) => {
        const name = rule.params.trim();
        if (/^[\w-]+\*?$/.test(name)) out.add(name);
    });
    return out;
}
// Only selector AST class nodes declare classes; URLs, values and comments never do.
export function parseClassSelectors(css) {
    const out = new Set();
    stylesheet(css).walkRules((rule) => {
        selectorParser()
            .astSync(rule.selector)
            .walkClasses((node) => out.add(node.value));
    });
    return out;
}
function isPackageFile(file) {
    return file.includes(`${path.sep}node_modules${path.sep}`);
}
// Files under node_modules contribute @utility names and class selectors
// (tw-animate-css declares animate-in that way) but not color tokens:
// Tailwind's own palette is not the project's vocabulary. Workspace
// packages resolve past node_modules and count as the project's own.
function readTheme(cssFile, seen, read, fromPackage = false) {
    if (seen.has(cssFile) || seen.size > 64) return;
    seen.add(cssFile);
    // Recorded before reading, so a missing file is seen when it appears.
    read.files.push(cssFile);
    let css;
    try {
        css = fs.readFileSync(cssFile, 'utf-8');
    } catch {
        return;
    }
    css = postcss.parse(css, { from: cssFile });
    for (const name of parseUtilities(css)) read.utilities.add(name);
    for (const name of parseClassSelectors(css)) read.classes.add(name);
    // Imports come first in the cascade.
    const dir = path.dirname(cssFile);
    for (const spec of parseImports(css)) {
        if (spec === 'tailwindcss' || spec.startsWith('tailwindcss/')) {
            read.tailwind = true;
        }
        const target = resolveStylesheet(dir, spec);
        if (!target) {
            read.missingImports.push({ spec, fromDir: dir });
            continue;
        }
        readTheme(target, seen, read, fromPackage || isPackageFile(target));
    }
    if (!fromPackage) {
        const { values, themeNames, declarations } = parseDeclarations(css);
        applyTokenDeclarations(declarations, read.tokens, read.scoped);
        for (const [name, value] of values) read.values.set(name, value);
        for (const name of themeNames) read.themeNames.add(name);
        read.declarations.push(...declarations);
    }
}
function signatureOf(read) {
    return [
        ...read.files.map((file) => `${file}:${mtimeOf(file) ?? 'missing'}`),
        ...read.missingImports.map(({ spec, fromDir }) => `${fromDir}:${spec}:${resolveStylesheet(fromDir, spec) ?? 'missing'}`),
    ].join('|');
}
function themeAt(cssFile) {
    const cached = cache.get(cssFile);
    const now = Date.now();
    if (cached && now - cached.checkedAt < TTL) return cached.read;
    if (cached && signatureOf(cached.read) === cached.signature) {
        cached.checkedAt = now;
        return cached.read;
    }
    const read = {
        tokens: new Set(),
        scoped: new Map(),
        utilities: new Set(),
        classes: new Set(),
        values: new Map(),
        themeNames: new Set(),
        declarations: [],
        tailwind: false,
        files: [],
        missingImports: [],
    };
    readTheme(cssFile, new Set(), read);
    cache.set(cssFile, {
        signature: signatureOf(read),
        checkedAt: now,
        read,
    });
    return read;
}
// Angular configurations name the theme explicitly; never guess a React project or silently use another stylesheet.
export function themeFileFor(fromFile) {
    const project = projectFor(fromFile);
    if (!project) return null;
    if (!isFile(project.cssFile)) throw new Error(`Design-system theme does not exist: ${project.cssFile}`);
    return project.cssFile;
}
export function tailwindEntryFor(fromFile) {
    return themeFileFor(fromFile);
}
export function colorTokensFor(fromFile) {
    const cssFile = themeFileFor(fromFile);
    if (!cssFile) return null;
    const { tokens } = themeAt(cssFile);
    return tokens.size ? tokens : null;
}
// Tokens declared under a color utility's own namespace, by namespace.
// Null outside a theme.
export function scopedColorTokensFor(fromFile) {
    const cssFile = themeFileFor(fromFile);
    if (!cssFile) return null;
    return themeAt(cssFile).scoped;
}
// Empty when the values cannot be read (color-mix, JS-set variables).
function colorsOf(read) {
    if (read.colors) return read.colors;
    const colors = new Map();
    for (const token of read.tokens) {
        const raw = read.values.get(`color-${token}`);
        if (!raw) continue;
        const resolved = resolveVariables(raw, read.values);
        const lab = resolved ? parseColor(resolved) : null;
        if (lab) colors.set(token, lab);
    }
    read.colors = colors;
    return colors;
}
const SCALE_DEFAULTS = {
    radius: RADII,
    text: FONT_SIZES,
};
// The theme's own --radius-* / --text-* declarations over Tailwind's
// defaults, in cascade order, the way Tailwind reads them.
function scaleOf(read, kind) {
    read.scales ??= {
        radius: buildScale(read, 'radius'),
        text: buildScale(read, 'text'),
    };
    return read.scales[kind];
}
// Tailwind's own steps, in pixels.
function defaultScale(kind) {
    const scale = new Map();
    for (const [name, value] of Object.entries(SCALE_DEFAULTS[kind])) {
        const px = lengthInPx(value);
        if (px !== null) scale.set(name, px);
    }
    return scale;
}
function buildScale(read, kind) {
    const scale = defaultScale(kind);
    const prefix = `${kind}-`;
    for (const { name, value, theme } of read.declarations) {
        if (!theme) continue;
        const reset = value.trim() === 'initial';
        if (name === '*') {
            if (reset) scale.clear();
            continue;
        }
        if (!name.startsWith(prefix) || name.includes('--')) continue;
        const step = name.slice(prefix.length);
        if (step === '*') {
            if (reset) scale.clear();
            continue;
        }
        if (reset) {
            scale.delete(step);
            continue;
        }
        const resolved = resolveVariables(value, read.values);
        const px = resolved ? lengthInPx(resolved) : null;
        if (px !== null) scale.set(step, px);
        else scale.delete(step);
    }
    return scale;
}
export const DEFAULT_SCALES = {
    radius: defaultScale('radius'),
    text: defaultScale('text'),
};
export function colorValuesFor(fromFile) {
    const cssFile = themeFileFor(fromFile);
    if (!cssFile) return null;
    const read = themeAt(cssFile);
    return read.tokens.size ? colorsOf(read) : null;
}
// Tailwind's 0.25rem unless the theme sets --spacing. Null when the theme
// removes it or sets it unreadably: no exact step can be named then.
export function spacingBaseFor(fromFile) {
    const cssFile = themeFileFor(fromFile);
    if (!cssFile) return 4;
    const read = themeAt(cssFile);
    if (read.spacing !== undefined) return read.spacing;
    let raw = '0.25rem';
    for (const { name, value, theme } of read.declarations) {
        if (!theme) continue;
        if (name === '*' && value.trim() === 'initial') raw = null;
        else if (name === 'spacing') raw = value.trim() === 'initial' ? null : value;
    }
    const resolved = raw === null ? null : resolveVariables(raw, read.values);
    const px = resolved ? lengthInPx(resolved) : null;
    return (read.spacing = px && px > 0 ? px : null);
}
export function scaleFor(fromFile, kind) {
    const cssFile = themeFileFor(fromFile);
    if (!cssFile) return DEFAULT_SCALES[kind];
    return scaleOf(themeAt(cssFile), kind);
}
// Null when the project has no theme to read.
export function themeVocabularyFor(fromFile) {
    const cssFile = themeFileFor(fromFile);
    if (!cssFile) return null;
    const read = themeAt(cssFile);
    return (read.vocabulary ??= {
        names: read.themeNames,
        tokens: read.tokens,
        utilities: read.utilities,
    });
}
const utilityPrefixes = new WeakMap();
// The `tab-` of an `@utility tab-*`, computed once per theme read.
export function utilityPrefixesOf(utilities) {
    let list = utilityPrefixes.get(utilities);
    if (!list) {
        list = [...utilities].filter((name) => name.endsWith('*')).map((name) => name.slice(0, -1));
        utilityPrefixes.set(utilities, list);
    }
    return list;
}
// Whether the project's CSS declares this class with @utility, by name
// or by prefix: Tailwind generates it, so it is the project's vocabulary
// whatever the name looks like. A plain selector is not: `.text-danger
// { color: #f00 }` is the raw color no-raw-colors exists to report.
export function declaresUtility(fromFile, token) {
    const base = normalizeClass(token).replace(OPACITY_MODIFIER, '');
    if (!base) return false;
    const { utilities } = knownClassesFor(fromFile);
    if (utilities.has(base)) return true;
    return utilityPrefixesOf(utilities).some((prefix) => base.startsWith(prefix));
}
// Whether the project's own CSS declares this class: an @utility name,
// an @utility prefix, or a class selector. Tailwind generates such a
// class, so no-restyle must not report it as a misspelling.
export function declaresClass(fromFile, token) {
    if (declaresUtility(fromFile, token)) return true;
    const base = normalizeClass(token).replace(OPACITY_MODIFIER, '');
    return !!base && knownClassesFor(fromFile).classes.has(base);
}
// What a project's CSS declares beyond Tailwind's own.
export function knownClassesFor(fromFile) {
    const cssFile = themeFileFor(fromFile);
    if (!cssFile) return { utilities: new Set(), classes: new Set() };
    const { utilities, classes } = themeAt(cssFile);
    return { utilities, classes };
}
