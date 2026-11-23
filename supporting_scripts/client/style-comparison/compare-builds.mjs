#!/usr/bin/env node
// Compares the styles of two builds of the client without a browser: the global stylesheet and the styles of the
// components, which the build embeds in the JavaScript chunks. See README.md.
import { globSync, readFileSync } from 'node:fs';
import postcss from 'postcss';
import { parseArguments, usage } from './lib.mjs';

const { options, positional } = parseArguments(process.argv.slice(2), { limit: '40' });
const [before, after] = positional;
usage('Usage: node compare-builds.mjs <build directory before> <build directory after> [--limit <declarations>] [--filter <text>]', options.help || !before || !after);

const normaliseValue = (value) =>
    value
        .replace(/(^|[^\d.])\.(\d)/g, '$10.$2')
        .replace(/\s*\/\s*/g, '/')
        .replace(/\s+/g, ' ')
        .replace(/"/g, "'")
        .trim();
const normaliseSelector = (selector) =>
    selector
        .replace(/\s+/g, ' ')
        .replace(/\s*([>+~,])\s*/g, '$1')
        // the build names the scope of a component differently in every build
        .replace(/\[_ng(content|host)-[\w%-]+\]/g, '[_ng$1]')
        .replace(/\[([^\]=]+=)['"]([^'"\]]*)['"]\]/g, '[$1$2]')
        .replace(/"/g, "'")
        .trim();

/** One line per declaration with the at-rules and the selector around it, so two stylesheets compare as sets of lines. */
function declarations(css, lines = new Map()) {
    const walk = (container, context) => {
        container.each((node) => {
            if (node.type === 'decl') {
                const line = `${context} { ${node.prop}: ${normaliseValue(node.value)}${node.important ? ' !important' : ''} }`;
                lines.set(line, (lines.get(line) ?? 0) + 1);
            } else if (node.type === 'rule') {
                walk(node, `${context}${normaliseSelector(node.selector)}`);
            } else if (node.type === 'atrule' && node.nodes) {
                walk(node, `${context}@${node.name} ${node.params.replace(/\s+/g, ' ')} | `);
            }
        });
    };
    walk(postcss.parse(css), '');
    return lines;
}

function globalStyles(directory) {
    const lines = new Map();
    for (const file of globSync(`${directory}/styles*.css`)) declarations(readFileSync(file, 'utf8'), lines);
    return lines;
}

function componentStyles(directory) {
    const lines = new Map();
    const literal = /"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`(?:[^`\\$]|\\.)*`/g;
    for (const file of globSync(`${directory}/*.js`)) {
        const source = readFileSync(file, 'utf8');
        for (const match of source.matchAll(/styles:\s*\[((?:\s*(?:"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`(?:[^`\\$]|\\.)*`)\s*,?)+)\]/g)) {
            for (const [text] of match[1].matchAll(literal)) {
                try {
                    // a string literal of the build output: JSON.parse reads it once its quotes are the ones of JSON
                    const css = text.startsWith('"') ? JSON.parse(text) : JSON.parse(`"${text.slice(1, -1).replace(/\\'/g, "'").replace(/"/g, '\\"').replace(/\n/g, '\\n')}"`);
                    declarations(css, lines);
                } catch {
                    // not a stylesheet
                }
            }
        }
    }
    return lines;
}

let different = false;
for (const [title, read] of [
    ['Global stylesheet', globalStyles],
    ['Component styles', componentStyles],
]) {
    const first = read(before);
    const second = read(after);
    const filter = (line) => !options.filter || line.includes(options.filter);
    const removed = [...first.keys()].filter((line) => !second.has(line) && filter(line));
    const added = [...second.keys()].filter((line) => !first.has(line) && filter(line));
    console.log(`\n${title}: ${first.size} distinct declarations before, ${second.size} after, ${removed.length} only before, ${added.length} only after`);
    for (const line of removed.slice(0, Number(options.limit))) console.log(`  - ${line.slice(0, 240)}`);
    for (const line of added.slice(0, Number(options.limit))) console.log(`  + ${line.slice(0, 240)}`);
    different ||= removed.length > 0 || added.length > 0;
}
process.exit(different ? 1 : 0);
