#!/usr/bin/env node
// Records the computed styles of every element on the pages and overlays of a running client. See README.md.
import { writeFileSync } from 'node:fs';
import { PROPERTIES, calm, collectStyles, login, nameFilter, openPage, outputDirectory, parseArguments, settle, usage, users } from './lib.mjs';
import { overlaysOf, pagesOf, resolveCourseContent } from './pages.mjs';

const { options, positional } = parseArguments(process.argv.slice(2), { base: 'http://localhost:9000', course: '9005', themes: 'light,dark' });
const label = positional[0];
usage('Usage: node capture.mjs <label> [--base <url>] [--course <id>] [--themes light,dark] [--only <text,text>] [--screenshots] [--out <directory>]', options.help || !label);
const base = options.base.replace(/\/$/, '');
const wanted = nameFilter(options.only);
const directory = outputDirectory(options, label, { create: true });
const problems = [];

const { browser, page } = await openPage();
page.on('pageerror', (error) => problems.push(`error on ${page.url().replace(base, '')}: ${error.message.slice(0, 160)}`));

await login(page, base, users.admin);
const ids = await resolveCourseContent(page, base, Number(options.course));
const pages = pagesOf(ids);
const overlays = overlaysOf(ids);
if (pages.skipped.length) {
    console.log(`Course ${ids.course} lacks content for ${pages.skipped.length} pages: ${pages.skipped.join(', ')}`);
}

async function record(name, theme, rootSelector) {
    const styles = await page.evaluate(collectStyles, { properties: PROPERTIES, rootSelector });
    writeFileSync(`${directory}/${name}-${theme}.json`, JSON.stringify(styles));
    if (options.screenshots) {
        await page.screenshot({ path: `${directory}/${name}-${theme}.png`, fullPage: rootSelector === 'body' }).catch(() => {});
    }
    return styles.elements.length;
}

for (const theme of options.themes.split(',')) {
    // The client follows the colour scheme of the system unless the user has chosen a theme.
    await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' });
    for (const role of ['admin', 'student']) {
        await login(page, base, users[role]);
        for (const [name, path] of pages[role].filter(([name]) => wanted(name))) {
            try {
                await page.goto(base + path);
                await settle(page);
                await calm(page);
                const count = await record(name, theme, 'body');
                console.log(`${name}-${theme}: ${count} elements`);
                if (!page.url().endsWith(path)) problems.push(`${name}-${theme}: landed on ${page.url().replace(base, '')}`);
            } catch (error) {
                problems.push(`${name}-${theme}: ${error.message.split('\n')[0].slice(0, 140)}`);
            }
        }
        for (const [name, , path, open] of overlays.filter(([name, user]) => user === role && wanted(name))) {
            try {
                await page.goto(base + path);
                await settle(page, 2000);
                await calm(page);
                await open(page);
                await page.waitForTimeout(700);
                const count = await record(name, theme, '.cdk-overlay-container');
                console.log(`${name}-${theme}: ${count} overlay elements`);
                if (!count) problems.push(`${name}-${theme}: no overlay opened`);
                await page.keyboard.press('Escape').catch(() => {});
            } catch (error) {
                problems.push(`${name}-${theme}: ${error.message.split('\n')[0].slice(0, 140)}`);
            }
        }
    }
}
await browser.close();
console.log(`\nCapture "${label}" written to ${directory}`);
if (problems.length) {
    console.log(`\n${problems.length} problems (a page that fails in both captures is a property of the data, not of the change):\n  ${[...new Set(problems)].join('\n  ')}`);
}
