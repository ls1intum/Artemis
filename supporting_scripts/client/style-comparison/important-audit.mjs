#!/usr/bin/env node
// Lists the `!important` declarations of the client that change nothing on the pages of the capture.
// See README.md and important-audit-browser.mjs.
import { readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';
import postcss from 'postcss';
import importantAudit from './important-audit-browser.mjs';
import { calm, login, nameFilter, openPage, parseArguments, repositoryRoot, settle, usage, users } from './lib.mjs';
import { overlaysOf, pagesOf, resolveCourseContent } from './pages.mjs';

const { options } = parseArguments(process.argv.slice(2), { base: 'http://localhost:9000', course: '9005' });
usage('Usage: node important-audit.mjs [--base <url>] [--course <id>] [--only <text,text>] [--all]', options.help);
const base = options.base.replace(/\/$/, '');
const wanted = nameFilter(options.only);

/** Shorthands whose longhands do not start with the name of the shorthand. */
const LONGHANDS = {
    inset: /^(?:top|right|bottom|left)$/,
    overflow: /^overflow-[xy]$/,
    gap: /^(?:row|column)-gap$/,
    'border-radius': /^border-.+-radius$/,
    'border-color': /^border-.+-color$/,
    'border-width': /^border-.+-width$/,
    'border-style': /^border-.+-style$/,
    'place-items': /^(?:align|justify)-items$/,
    'place-content': /^(?:align|justify)-content$/,
    'grid-area': /^grid-(?:row|column)-(?:start|end)$/,
};
const covers = (property, longhand) => longhand === property || longhand.startsWith(`${property}-`) || LONGHANDS[property]?.test(longhand) === true;

/** One spelling for a selector of the source and for the one the browser reports for it. */
const normalise = (selector) =>
    selector
        .replace(/::ng-deep/g, ' ')
        .replace(/:host\(([^()]*(?:\([^()]*\))?[^()]*)\)/g, '$1:host')
        .replace(/"/g, "'")
        .replace(/\s*([>+~,])\s*/g, '$1')
        .replace(/\s+/g, ' ')
        .trim();

function stylesheets(directory) {
    return readdirSync(directory, { withFileTypes: true, recursive: true })
        .filter((entry) => entry.isFile() && entry.name.endsWith('.css') && !entry.parentPath.includes('node_modules'))
        .map((entry) => join(entry.parentPath, entry.name));
}

/** Every `!important` declaration of the source: `{ file, line, selector, property }`. */
function sourceDeclarations() {
    const declarations = [];
    for (const file of [...stylesheets(join(repositoryRoot, 'src/main/webapp')), ...stylesheets(join(repositoryRoot, 'packages/tum-aet-ui/src'))]) {
        postcss.parse(readFileSync(file, 'utf8')).walkDecls((declaration) => {
            if (declaration.important && declaration.parent.type === 'rule') {
                declarations.push({
                    file: relative(repositoryRoot, file),
                    line: declaration.source.start.line,
                    selector: normalise(declaration.parent.selector),
                    property: declaration.prop,
                });
            }
        });
    }
    return declarations;
}

const seen = new Map();
async function audit(name) {
    for (const entry of await page.evaluate(importantAudit)) {
        const key = `${normalise(entry.selector)}|${entry.property}`;
        const total = seen.get(key) ?? { selector: normalise(entry.selector), property: entry.property, matched: 0, changed: 0, example: '' };
        total.matched += entry.matched;
        total.changed += entry.changed;
        if (entry.changed && !total.example) total.example = `${entry.example} on ${name}`;
        seen.set(key, total);
    }
}

const { browser, page } = await openPage();
await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' });
await login(page, base, users.admin);
const ids = await resolveCourseContent(page, base, Number(options.course));
const pages = pagesOf(ids);
for (const role of ['admin', 'student']) {
    await login(page, base, users[role]);
    for (const [name, path] of pages[role].filter(([pageName]) => wanted(pageName))) {
        await page.goto(base + path);
        await settle(page);
        await calm(page);
        await audit(name);
        console.error(name);
    }
    for (const [name, , path, open] of overlaysOf(ids).filter(([overlayName, user]) => user === role && wanted(overlayName))) {
        try {
            await page.goto(base + path);
            await settle(page, 2000);
            await calm(page);
            await open(page);
            await page.waitForTimeout(700);
            await audit(name);
            console.error(name);
        } catch (error) {
            console.error(`${name}: ${error.message.split('\n')[0].slice(0, 140)}`);
        }
    }
}
await browser.close();

const groups = { 'changes nothing': [], needed: [], 'matched no element': [] };
for (const declaration of sourceDeclarations()) {
    const entries = [...seen.values()].filter((entry) => entry.selector === declaration.selector && covers(declaration.property, entry.property));
    const matched = Math.max(0, ...entries.map((entry) => entry.matched));
    const changed = entries.reduce((sum, entry) => sum + entry.changed, 0);
    const example = entries.find((entry) => entry.example)?.example ?? '';
    // `calm()` switches animations and transitions off, so the priority of these declarations cannot be judged.
    const judged = matched > 0 && !/^(?:animation|transition)/.test(declaration.property);
    const group = !judged ? 'matched no element' : changed ? 'needed' : 'changes nothing';
    groups[group].push(`${declaration.file}:${declaration.line}  ${declaration.selector} { ${declaration.property} }${example ? `  (${example})` : ''}`);
}
for (const [title, rows] of Object.entries(groups)) {
    console.log(`\n${rows.length} declarations: ${title}`);
    if (title === 'changes nothing' || options.all) {
        console.log(rows.map((row) => `  ${row}`).join('\n'));
    }
}
