#!/usr/bin/env node
/**
 * Read-only helpers for the Bootstrap -> Tailwind/PrimeNG migration. Thin ergonomics over the gates that already
 * exist (the no-bootstrap-classes / stylelint locks + the migration-source-coverage consistency test) — they add
 * NO new source of truth and gate nothing on their own.
 *
 *   node supporting_scripts/migration/migrate.mjs status        # burndown: remaining Bootstrap per section
 *   node supporting_scripts/migration/migrate.mjs check <path>  # is <path> Bootstrap-free / ready to lock?
 *
 * Both reuse the EXACT curated matcher from the no-bootstrap-classes ESLint rule (isBanned), so counts agree with
 * the lint gate instead of a naive grep that over-reports shared spacing utilities (mb-*, gap-*, ...).
 */
import { readFileSync, readdirSync, existsSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, relative } from 'node:path';
import { isBanned, bannedClassesInBindingExpression } from '../../rules/no-bootstrap-classes.mjs';

const APP = resolve(dirname(fileURLToPath(import.meta.url)), '../../src/main/webapp/app');

function walk(dir, ext) {
    if (!existsSync(dir)) return [];
    if (statSync(dir).isFile()) return dir.endsWith(ext) ? [dir] : [];
    return readdirSync(dir, { recursive: true })
        .filter((p) => p.endsWith(ext))
        .map((p) => resolve(dir, p));
}

// Bootstrap class tokens in a template, matching what the no-bootstrap-classes ESLint rule checks: static
// class="...", [class.token] (which naturally skips component inputs like [card]), and the bound [class]/[ngClass]
// expression forms (`[class]="'btn ' + x"`, `[ngClass]="{ btn: cond }"`) via the rule's shared extractor — so a
// `migrate:check` "ready to lock" can't disagree with the lint. Approximate by design — a burndown trend.
function bootstrapClassesInHtml(text) {
    const found = [];
    const scanList = (value) => {
        for (const t of value.split(/\s+/)) if (t && isBanned(t)) found.push(t);
    };
    // static class="..." and PrimeNG styleClass="..." / *StyleClass="..."
    for (const m of text.matchAll(/\sclass="([^"]*)"/g)) scanList(m[1]);
    for (const m of text.matchAll(/\s[\w-]*[Ss]tyleClass="([^"]*)"/g)) scanList(m[1]);
    // [class.token]
    for (const m of text.matchAll(/\[class\.([\w-]+)\]/g)) if (isBanned(m[1])) found.push(m[1]);
    // bound [class]/[ngClass]/[styleClass]/[*StyleClass] expressions
    for (const m of text.matchAll(/\[(?:ngClass|class|[\w-]*[Ss]tyleClass)\]="([^"]*)"/g)) found.push(...bannedClassesInBindingExpression(m[1]));
    return found;
}

// SCSS Bootstrap residue: --bs-* variables and hardcoded hex colours (what the stylelint override bans).
function bootstrapInScss(text) {
    return [...text.matchAll(/var\(--bs-[\w-]+\)|#[0-9a-fA-F]{3,8}\b/g)].map((m) => m[0]);
}

function scan(files, finder) {
    const perFile = new Map();
    let total = 0;
    for (const f of files) {
        const found = finder(readFileSync(f, 'utf8'));
        if (found.length) {
            perFile.set(f, found);
            total += found.length;
        }
    }
    return { perFile, total };
}

const [cmd, arg] = process.argv.slice(2);

if (cmd === 'status') {
    const { perFile } = scan(walk(APP, '.html'), bootstrapClassesInHtml);
    const bySection = new Map();
    for (const [f, found] of perFile) {
        const section = relative(APP, f).split('/')[0];
        const s = bySection.get(section) ?? { files: 0, hits: 0 };
        s.files++;
        s.hits += found.length;
        bySection.set(section, s);
    }
    const rows = [...bySection].sort((a, b) => b[1].hits - a[1].hits);
    console.log('Remaining Bootstrap classes by section (templates):\n');
    console.log('  ' + 'section'.padEnd(38) + 'files'.padStart(6) + 'hits'.padStart(8));
    let tFiles = 0;
    let tHits = 0;
    for (const [section, s] of rows) {
        console.log('  ' + section.padEnd(38) + String(s.files).padStart(6) + String(s.hits).padStart(8));
        tFiles += s.files;
        tHits += s.hits;
    }
    console.log('  ' + '-'.repeat(52));
    console.log('  ' + 'TOTAL'.padEnd(38) + String(tFiles).padStart(6) + String(tHits).padStart(8));
    console.log('\n(Approximate — reuses the no-bootstrap-classes matcher. Locked sections read ~0; lint the path for the authoritative gate.)');
} else if (cmd === 'check') {
    if (!arg) {
        console.error('usage: migrate.mjs check <path under src/main/webapp/app>');
        process.exit(2);
    }
    const base = resolve(APP, arg.replace(/^.*\/app\//, ''));
    // Guard against paths that escape the app tree (e.g. `../..`) or a typo-under-APP that resolves inside
    // APP but does not exist. The startsWith check alone misses the latter, so pair it with existsSync.
    if ((!base.startsWith(APP + '/') && base !== APP) || !existsSync(base)) {
        console.error(`error: ${arg} does not resolve to an existing path under src/main/webapp/app`);
        process.exit(2);
    }
    const html = scan(walk(base, '.html'), bootstrapClassesInHtml);
    const scss = scan(walk(base, '.scss'), bootstrapInScss);
    for (const [f, found] of html.perFile) console.log(`  [html] ${relative(APP, f)} — ${[...new Set(found)].join(', ')}`);
    for (const [f, found] of scss.perFile) console.log(`  [scss] ${relative(APP, f)} — ${[...new Set(found)].join(', ')}`);
    const clean = html.total === 0 && scss.total === 0;
    console.log(
        clean
            ? `\n✓ ${arg} is Bootstrap-free — ready to lock (add it to the 3 lists; migration-source-coverage checks consistency).`
            : `\n✗ ${arg} still has Bootstrap (${html.total} class hits, ${scss.total} scss hits) — not ready to lock.`,
    );
    process.exit(clean ? 0 : 1);
} else {
    console.error('usage: migrate.mjs status | check <path>');
    process.exit(2);
}
