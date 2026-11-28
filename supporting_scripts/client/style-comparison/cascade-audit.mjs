#!/usr/bin/env node
// Lists where the declarations of one cascade layer lose although their selector is the more specific one.
// See README.md and cascade-audit-browser.mjs.
import cascadeAudit from './cascade-audit-browser.mjs';
import { calm, login, nameFilter, openPage, parseArguments, settle, usage, users } from './lib.mjs';
import { pagesOf, resolveCourseContent } from './pages.mjs';

const { options } = parseArguments(process.argv.slice(2), { base: 'http://localhost:9000', course: '9005', layer: 'tumaet-ui', limit: '60' });
usage('Usage: node cascade-audit.mjs [--base <url>] [--course <id>] [--layer <name>] [--only <text,text>] [--limit <groups>]', options.help);
const base = options.base.replace(/\/$/, '');
const wanted = nameFilter(options.only);

const { browser, page } = await openPage();
await page.emulateMedia({ colorScheme: 'light', reducedMotion: 'reduce' });
await login(page, base, users.admin);
const pages = pagesOf(await resolveCourseContent(page, base, Number(options.course)));

const groups = new Map();
for (const role of ['admin', 'student']) {
    await login(page, base, users[role]);
    for (const [name, path] of pages[role].filter(([name]) => wanted(name))) {
        await page.goto(base + path);
        await settle(page);
        await calm(page);
        const { conflicts } = await page.evaluate(cascadeAudit, options.layer);
        for (const conflict of conflicts) {
            const key = `${conflict.type}|${conflict.host}`;
            if (!groups.has(key)) groups.set(key, { type: conflict.type, winner: conflict.host, properties: new Set(), losers: new Set(), pages: new Set(), example: conflict.ex });
            const group = groups.get(key);
            group.properties.add(conflict.prop);
            group.losers.add(conflict.kit.replace(/\[_ng(content|host)-[\w-]+\]/g, ''));
            group.pages.add(name);
        }
        console.error(`${name}: ${conflicts.length} conflicts`);
    }
}
await browser.close();

const rows = [...groups.values()].sort((first, second) => first.type.localeCompare(second.type) || second.pages.size - first.pages.size);
console.log(`${rows.length} rules win against the layer "${options.layer}" although its selector is the more specific one`);
for (const row of rows.slice(0, Number(options.limit))) {
    console.log(`\n${row.type} on ${row.pages.size} pages (${[...row.pages].slice(0, 3).join(', ')})`);
    console.log(`  winner:     ${row.winner.slice(0, 160)}`);
    console.log(`  properties: ${[...row.properties].join(', ').slice(0, 160)}`);
    console.log(`  loses:      ${[...row.losers].slice(0, 3).join(' | ').slice(0, 220)}`);
    if (row.example) console.log(`  element:    ${String(row.example).slice(0, 200)}`);
}
