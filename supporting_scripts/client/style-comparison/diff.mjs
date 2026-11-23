#!/usr/bin/env node
// Compares two captures of capture.mjs and groups the differences. See README.md.
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { outputDirectory, parseArguments, usage } from './lib.mjs';

const { options, positional } = parseArguments(process.argv.slice(2), { limit: '80' });
const [before, after] = positional;
usage('Usage: node diff.mjs <label before> <label after> [--only <regex>] [--limit <groups>] [--geometry] [--out <directory>]', options.help || !before || !after);
const only = options.only ? new RegExp(options.only) : undefined;
const beforeDirectory = outputDirectory(options, before);
const afterDirectory = outputDirectory(options, after);

// The size and position of an element follow from the other properties and from the data; they are compared on request.
const GEOMETRY = new Set(['width', 'height']);
const BORDER_WIDTHS = ['border-top-width', 'border-right-width', 'border-bottom-width', 'border-left-width'];
const normalise = (value) => (value === 'start' ? 'left' : value === 'end' ? 'right' : value);

const groups = new Map();
const structure = [];
let compared = 0;
for (const file of readdirSync(afterDirectory).filter((name) => name.endsWith('.json'))) {
    const state = file.replace('.json', '');
    if (only && !only.test(state)) continue;
    if (!existsSync(`${beforeDirectory}/${file}`)) {
        structure.push(`${state}: only in "${after}"`);
        continue;
    }
    const first = JSON.parse(readFileSync(`${beforeDirectory}/${file}`, 'utf8'));
    const second = JSON.parse(readFileSync(`${afterDirectory}/${file}`, 'utf8'));
    compared++;
    if (first.elements.length !== second.elements.length) {
        structure.push(`${state}: ${first.elements.length} elements before, ${second.elements.length} after`);
    }
    const properties = first.properties;
    const borderIndexes = BORDER_WIDTHS.map((property) => properties.indexOf(property));
    const noBorder = (values) => borderIndexes.every((index) => values[index] === '0px');
    const afterByPath = new Map(second.elements.map((element) => [element[0], element]));
    for (const [path, classes, values] of first.elements) {
        const other = afterByPath.get(path);
        if (!other) continue;
        const tag = path.split('/').pop().split(':')[0];
        properties.forEach((property, index) => {
            if (GEOMETRY.has(property) && !options.geometry) return;
            // the colour and style of a border without a width are not visible
            if (/^border-.*-(color|style)$/.test(property) && noBorder(values) && noBorder(other[2])) return;
            if (normalise(values[index]) === normalise(other[2][index])) return;
            const key = `<${tag}> ${property}: ${values[index]} -> ${other[2][index]}   class="${classes.slice(0, 80)}"${classes === other[1] ? '' : ` -> "${other[1].slice(0, 80)}"`}`;
            if (!groups.has(key)) groups.set(key, new Set());
            groups.get(key).add(state);
        });
    }
}
const rows = [...groups].sort((first, second) => second[1].size - first[1].size);
console.log(`${compared} states compared, ${rows.length} distinct differences`);
if (structure.length) {
    console.log(`\nStates with a different number of elements (compare those by eye, the paths of their elements have shifted):\n  ${structure.join('\n  ')}`);
}
for (const [key, states] of rows.slice(0, Number(options.limit))) {
    console.log(`${String(states.size).padStart(4)}  ${key}   [${[...states].slice(0, 3).join(', ')}]`);
}
process.exit(rows.length || structure.length ? 1 : 0);
