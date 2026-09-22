import process from 'node:process';
// Asks the project's own Tailwind whether a class generates CSS, from a
// design system built the way Tailwind builds it, imports and plugins
// included. Runs in a worker thread (worker.mjs), reached synchronously
// through client.mjs; everything here is async and thread-agnostic.
import * as fs from 'node:fs';
import { createRequire } from 'node:module';
import * as path from 'node:path';
import { pathToFileURL } from 'node:url';
import enhancedResolve from 'enhanced-resolve';
import { loadModule } from '@tailwindcss/node';
import { splitVariants } from '../grammar/classes.mjs';
import { didYouMean } from '../grammar/similar.mjs';
const SIGNATURE_TTL = 1000;
const loaded = new Map();
let generations = 0;
function resolveFrom(base, id) {
    return createRequire(path.join(base, 'noop.js')).resolve(id);
}
// Resolved from the stylesheet outward: ours is not a substitute for the
// version that actually generates the project's CSS.
async function loadTailwind(dir) {
    for (const from of [dir, process.cwd()]) {
        let resolved;
        try {
            resolved = resolveFrom(from, 'tailwindcss');
        } catch {
            continue;
        }
        // Tailwind's entry is CommonJS: its API may sit on `default`.
        const mod = await import(pathToFileURL(resolved).href);
        const api = typeof mod.__unstable__loadDesignSystem === 'function' ? mod : typeof mod.default?.__unstable__loadDesignSystem === 'function' ? mod.default : null;
        if (api) return api;
        throw new Error(`${resolved} is not Tailwind v4 (no __unstable__loadDesignSystem)`);
    }
    return null;
}
// Match Tailwind's CSS resolution conditions, including package exports and pnpm symlinks.
const cssResolver = enhancedResolve.ResolverFactory.createResolver({
    fileSystem: fs,
    useSyncFileSystemCalls: true,
    extensions: ['.css'],
    mainFields: ['style'],
    conditionNames: ['style'],
    modules: ['node_modules', ...(process.env.NODE_PATH ? process.env.NODE_PATH.split(path.delimiter) : [])],
});
export function resolveStylesheet(base, id) {
    try {
        return cssResolver.resolveSync({}, base, id) || null;
    } catch {
        return null;
    }
}
// The real path of a file reached through a symlink: what its own
// imports resolve from.
function realpathOf(file) {
    try {
        return fs.realpathSync.native(file);
    } catch {
        return file;
    }
}
function mtimeOf(file) {
    try {
        return fs.statSync(file).mtimeMs;
    } catch {
        return null;
    }
}
function signatureOf(files) {
    return files.map((file) => `${file}:${mtimeOf(file) ?? 'missing'}`).join('|');
}
async function build(cssFile) {
    const dir = path.dirname(cssFile);
    const tailwind = await loadTailwind(dir);
    if (!tailwind) {
        throw new Error(`tailwindcss v4 could not be resolved from ${dir}`);
    }
    const files = [cssFile];
    let modules = 0;
    const css = fs.readFileSync(cssFile, 'utf-8');
    const ds = await tailwind.__unstable__loadDesignSystem(css, {
        base: dir,
        async loadStylesheet(id, base) {
            if (/^(?:https?:|data:)/.test(id)) return { base, content: '' };
            const resolved = resolveStylesheet(base, id);
            // Refusing to judge beats judging against half a theme.
            if (!resolved) {
                throw new Error(`@import "${id}" could not be resolved from ${base}`);
            }
            // A pnpm-installed package is a link into node_modules/.pnpm and its
            // own dependencies sit beside the real file, so the base for the
            // imports inside it is the directory that file really lives in.
            const file = realpathOf(resolved);
            files.push(file);
            return {
                base: path.dirname(file),
                content: fs.readFileSync(file, 'utf-8'),
            };
        },
        async loadModule(id, base) {
            const loaded = await loadModule(id, base, (file) => files.push(file));
            files.push(loaded.path);
            modules++;
            return loaded;
        },
    });
    const variants = ds.getVariants();
    return {
        ds,
        files,
        modules,
        prefix: ds.theme?.prefix ?? null,
        signature: signatureOf(files),
        checkedAt: Date.now(),
        generation: ++generations,
        classNames: null,
        staticVariants: variants.filter((v) => !v.isArbitrary && !v.values.length).map((v) => v.name),
        functionalVariants: variants.filter((v) => v.isArbitrary || v.values.length).map((v) => v.name),
    };
}
async function designSystemFor(cssFile) {
    const cached = loaded.get(cssFile);
    const now = Date.now();
    if (cached) {
        if (now - cached.checkedAt < SIGNATURE_TTL) return cached;
        if (signatureOf(cached.files) === cached.signature) {
            cached.checkedAt = now;
            return cached;
        }
    }
    const fresh = await build(cssFile);
    loaded.set(cssFile, fresh);
    return fresh;
}
function classNamesOf(system) {
    if (!system.classNames) {
        system.classNames = system.ds.getClassList().map((entry) => (Array.isArray(entry) ? entry[0] : entry));
    }
    return system.classNames;
}
function variantKnown(system, variant) {
    const name = variant.replace(/\/.*$/, '');
    if (name.startsWith('[') || name.startsWith('@')) return true;
    if (system.staticVariants.includes(name)) return true;
    return system.functionalVariants.some((prefix) => name === prefix || name.startsWith(`${prefix}-`));
}
// A base utility that exists on its own means the variant is misspelled;
// otherwise the utility is, matched against every class Tailwind knows.
function suggestionFor(system, candidate) {
    const { variants, base } = splitVariants(candidate);
    // With a prefix, the bare utility is only valid as tw:flex.
    const prefixed = system.prefix !== null && variants[0] === system.prefix;
    const probe = prefixed ? `${system.prefix}:${base}` : base;
    if (variants.length && system.ds.candidatesToCss([probe])[0] !== null) {
        let changed = false;
        const fixed = variants.map((variant, i) => {
            if (prefixed && i === 0) return variant;
            if (variantKnown(system, variant)) return variant;
            const meant = didYouMean(variant, system.staticVariants, 1);
            if (!meant) return variant;
            changed = true;
            return meant;
        });
        return changed ? [...fixed, base].join(':') : null;
    }
    const bare = base.replace(/^!/, '').replace(/!$/, '').replace(/^-/, '');
    const meant = didYouMean(bare, classNamesOf(system));
    if (!meant) return null;
    const rebuilt = base.replace(bare, meant);
    return [...variants, rebuilt].join(':');
}
// The project's prefix is a variant segment in source (tw:hover:flex) and
// must stay on the bare utility (tw:flex).
function baseKnownOf(system, candidate) {
    const { variants, base } = splitVariants(candidate);
    if (!variants.length) return false;
    const bare = system.prefix && variants[0] === system.prefix ? `${system.prefix}:${base}` : base;
    if (variants.length === (bare === base ? 0 : 1)) return false;
    return system.ds.candidatesToCss([bare])[0] !== null;
}
export async function query(cssFile, candidates) {
    let system;
    try {
        system = await designSystemFor(cssFile);
    } catch (error) {
        return { ok: false, reason: error.message };
    }
    const css = system.ds.candidatesToCss(candidates);
    const unknown = [];
    for (let i = 0; i < candidates.length; i++) {
        if (css[i] !== null) continue;
        unknown.push({
            token: candidates[i],
            suggestion: suggestionFor(system, candidates[i]),
            baseKnown: baseKnownOf(system, candidates[i]),
        });
    }
    return {
        ok: true,
        generation: system.generation,
        hasModules: system.modules > 0,
        unknown,
    };
}
