#!/usr/bin/env node
// Lists the `!important` declarations of the client that change nothing on the pages of the capture.
// See README.md and important-audit-browser.mjs.
import { readFileSync, readdirSync } from 'node:fs';
import { join, relative } from 'node:path';
import postcss from 'postcss';
import importantAudit from './important-audit-browser.mjs';
import { calm, login, nameFilter, openPage, parseArguments, repositoryRoot, settle, usage, users } from './lib.mjs';
import { classify, normalise, observe } from './important-audit-report.mjs';
import { overlaysOf, pagesOf, resolveCourseContent } from './pages.mjs';

const { options } = parseArguments(process.argv.slice(2), { base: 'http://localhost:9000', course: '9005' });
usage('Usage: node important-audit.mjs [--base <url>] [--course <id>] [--only <text,text>] [--all]', options.help);
const base = options.base.replace(/\/$/, '');
const wanted = nameFilter(options.only);

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

const observations = new Map();
const audit = async (name) => observe(observations, await page.evaluate(importantAudit), name);

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

const groups = classify(sourceDeclarations(), observations);
for (const [title, rows] of Object.entries(groups)) {
    console.log(`\n${rows.length} declarations: ${title}`);
    if (title === 'changes nothing' || options.all) {
        console.log(rows.map((row) => `  ${row}`).join('\n'));
    }
}
