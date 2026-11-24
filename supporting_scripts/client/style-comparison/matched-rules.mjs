#!/usr/bin/env node
// Prints the rules that match one element for the given properties, with their cascade layer, in the order in which the
// browser applies them (the last one wins unless an earlier one is important). See README.md.
import { calm, login, openPage, parseArguments, settle, usage, users } from './lib.mjs';

const { options } = parseArguments(process.argv.slice(2), { base: 'http://localhost:9000', user: 'admin', theme: 'light' });
usage(
    'Usage: node matched-rules.mjs --url <path> --selector <css selector> --properties <a,b> [--base <url>] [--user admin|student] [--theme light|dark]',
    options.help || !options.url || !options.selector || !options.properties,
);
const base = options.base.replace(/\/$/, '');
const properties = options.properties.split(',');
const related = (name) => properties.some((property) => name === property || name.startsWith(`${property}-`) || property.startsWith(`${name}-`));

const { browser, page } = await openPage();
await page.emulateMedia({ colorScheme: options.theme, reducedMotion: 'reduce' });
await login(page, base, users[options.user]);
await page.goto(base + options.url);
await settle(page, 2000);
await calm(page);

const session = await page.context().newCDPSession(page);
await session.send('DOM.enable');
await session.send('CSS.enable');
const { root } = await session.send('DOM.getDocument', { depth: 0 });
const { nodeId } = await session.send('DOM.querySelector', { nodeId: root.nodeId, selector: options.selector });
if (!nodeId) {
    console.error(`No element matches ${options.selector} on ${options.url}`);
    await browser.close();
    process.exit(1);
}
const matched = await session.send('CSS.getMatchedStylesForNode', { nodeId });
const describe = (style) =>
    style.cssProperties
        .filter((property) => related(property.name) && property.text !== undefined)
        .map((property) => `${property.name}: ${property.value}${property.important ? ' !important' : ''}`)
        .join('; ');
console.log(`class="${(await page.locator(options.selector).first().getAttribute('class')) ?? ''}"`);
for (const { rule } of matched.matchedCSSRules) {
    const text = describe(rule.style);
    if (!text) continue;
    const layer = rule.layers?.length ? rule.layers.map((entry) => entry.text).join('.') : 'no layer';
    console.log(`[${layer}] ${rule.selectorList.text.slice(0, 120)} { ${text} }`);
}
if (matched.inlineStyle && describe(matched.inlineStyle)) {
    console.log(`[inline style] { ${describe(matched.inlineStyle)} }`);
}
const computed = await page
    .locator(options.selector)
    .first()
    .evaluate((element, names) => names.map((name) => `${name}: ${getComputedStyle(element).getPropertyValue(name)}`), properties);
console.log(`computed: ${computed.join('; ')}`);
await browser.close();
