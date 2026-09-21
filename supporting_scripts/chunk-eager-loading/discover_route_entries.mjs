#!/usr/bin/env node
/**
 * Discovers every Angular route's lazy-loaded component (`loadComponent: () => import(...)`)
 * under src/main/webapp/app, replacing the old hand-curated ROUTE_ENTRIES list that only covered
 * the two routes PR #13027 happened to fix. A route nobody remembers to add to a curated list is
 * a route this guard silently doesn't watch -- this closes that gap by deriving the full set
 * straight from the route config source, the same place Angular itself reads it from.
 *
 * Deliberately regex-based, not a full TypeScript AST parse: every `loadComponent` in this
 * codebase follows the exact shape `loadComponent: () => import('<specifier>')...`, so matching
 * that shape directly is simpler and faster than parsing, and gives the same result. Only
 * `loadComponent` is matched, not `loadChildren` (a handful of routes lazy-load a child *routes*
 * module rather than a single component) -- that's a different chunk shape (a route config with
 * its own further static/dynamic children) than the "one component, one chunk" model the rest of
 * this guard's eager-set analysis assumes, and out of scope for the bug class this guards against.
 *
 * Usage:
 *   node discover_route_entries.mjs            # prints the discovered { slug: sourcePath } map
 */

import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname, resolve, relative, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const APP_ROOT = join(REPO_ROOT, 'src/main/webapp/app');

// Requires `import(` to immediately follow `loadComponent: () =>` (only whitespace/newlines
// between them) so this can't accidentally wander into an unrelated import() elsewhere in the
// file -- every real occurrence in this codebase has that exact shape, split across one or two lines.
const LOAD_COMPONENT_RE = /loadComponent:\s*\(\)\s*=>\s*import\(\s*['"]([^'"]+)['"]\s*\)/g;

function findRouteFiles(dir, out = []) {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
        const full = join(dir, entry.name);
        if (entry.isDirectory()) findRouteFiles(full, out);
        else if (entry.isFile() && entry.name.endsWith('.route.ts')) out.push(full);
    }
    return out;
}

/** Resolves an import specifier from a route file to an absolute .ts path, or null if it can't be resolved. */
function resolveSpecifier(specifier, routeFileDir) {
    let withoutExt;
    if (specifier.startsWith('.')) {
        withoutExt = resolve(routeFileDir, specifier);
    } else if (specifier.startsWith('app/')) {
        // Mirrors tsconfig.json's "app/*" -> "./src/main/webapp/app/*" path mapping.
        withoutExt = join(APP_ROOT, specifier.slice('app/'.length));
    } else {
        return null; // no other alias is used for a loadComponent target in this codebase
    }
    const withExt = `${withoutExt}.ts`;
    return existsSync(withExt) ? withExt : null;
}

/**
 * Derives a stable, human-readable route key from a component's file path -- matching the
 * naming already used by the old hand-curated list (e.g. "course-overview" for
 * course-overview.component.ts), so existing reports/thresholds read the same way. Two unrelated
 * features naming their entry component the same thing (e.g. both called "list.component.ts") is
 * disambiguated with the parent directory name rather than silently overwriting one of them.
 */
function slugFor(sourcePath, usedSlugs) {
    const base = basename(sourcePath, '.component.ts');
    if (!usedSlugs.has(base)) return base;
    const qualified = `${basename(dirname(sourcePath))}-${base}`;
    if (!usedSlugs.has(qualified)) return qualified;
    return relative(REPO_ROOT, sourcePath)
        .replace(/\\/g, '/')
        .replace(/\.component\.ts$/, '')
        .replace(/\//g, '-');
}

export function discoverRouteEntries() {
    const routeFiles = findRouteFiles(APP_ROOT);

    // Keyed by repo-relative source path so the same component referenced from many route entries
    // (e.g. one detail component reused across text/quiz/modeling/... exercise routes) is a single
    // guarded entry, not one per usage site.
    const sourcePaths = new Set();
    for (const routeFile of routeFiles) {
        const content = readFileSync(routeFile, 'utf8');
        for (const match of content.matchAll(LOAD_COMPONENT_RE)) {
            const resolved = resolveSpecifier(match[1], dirname(routeFile));
            if (!resolved) continue; // unresolvable specifier is a config problem elsewhere, not this script's job
            sourcePaths.add(relative(REPO_ROOT, resolved).replace(/\\/g, '/'));
        }
    }

    const usedSlugs = new Set();
    const entries = {};
    for (const sourcePath of [...sourcePaths].sort()) {
        const slug = slugFor(sourcePath, usedSlugs);
        usedSlugs.add(slug);
        entries[slug] = sourcePath;
    }
    return entries;
}

function main() {
    const entries = discoverRouteEntries();
    console.log(JSON.stringify(entries, null, 2));
    console.error(`Discovered ${Object.keys(entries).length} lazy-loaded route components.`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) main();
