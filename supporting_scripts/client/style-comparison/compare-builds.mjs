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

const ESCAPES = { n: '\n', t: '\t', r: '\r', b: '\b', f: '\f', v: '\v', 0: '\0' };

/** Reads the JavaScript string literal that starts at `start`: its value and the position after it. */
function readStringLiteral(source, start) {
    const quote = source[start];
    let value = '';
    let index = start + 1;
    while (index < source.length && source[index] !== quote) {
        const character = source[index];
        if (character === '\\') {
            const next = source[index + 1];
            if (next === 'u' && source[index + 2] === '{') {
                const end = source.indexOf('}', index);
                value += String.fromCodePoint(parseInt(source.slice(index + 3, end), 16));
                index = end + 1;
            } else if (next === 'u') {
                value += String.fromCharCode(parseInt(source.slice(index + 2, index + 6), 16));
                index += 6;
            } else if (next === 'x') {
                value += String.fromCharCode(parseInt(source.slice(index + 2, index + 4), 16));
                index += 4;
            } else {
                // a backslash before a line break continues the literal on the next line
                value += next === '\n' ? '' : (ESCAPES[next] ?? next);
                index += 2;
            }
        } else if (quote === '`' && character === '$' && source[index + 1] === '{') {
            // a template with an expression is not a stylesheet of the build
            return undefined;
        } else {
            value += character;
            index++;
        }
    }
    return { value, end: index + 1 };
}

/** The build writes the styles of a component as `styles: ["…", "…"]` into the chunk of the component. */
function componentStyles(directory) {
    const lines = new Map();
    const marker = 'styles:';
    for (const file of globSync(`${directory}/*.js`)) {
        const source = readFileSync(file, 'utf8');
        let position = 0;
        while ((position = source.indexOf(marker, position)) !== -1) {
            let index = position + marker.length;
            while (source[index] === ' ' || source[index] === '\n') index++;
            position = index;
            if (source[index] !== '[') continue;
            index++;
            for (;;) {
                while (' \n,'.includes(source[index])) index++;
                if (!'"\'`'.includes(source[index])) break;
                const literal = readStringLiteral(source, index);
                if (!literal) break;
                try {
                    declarations(literal.value, lines);
                } catch {
                    // not a stylesheet
                }
                index = literal.end;
            }
            position = index;
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
    for (const [directory, lines] of [
        [before, first],
        [after, second],
    ]) {
        if (lines.size === 0) {
            console.error(`${title}: no styles found in ${directory}. Pass the output directory of a client build (build/resources/main/static).`);
            process.exit(2);
        }
    }
    const filter = (line) => !options.filter || line.includes(options.filter);
    const removed = [...first.keys()].filter((line) => !second.has(line) && filter(line));
    const added = [...second.keys()].filter((line) => !first.has(line) && filter(line));
    console.log(`\n${title}: ${first.size} distinct declarations before, ${second.size} after, ${removed.length} only before, ${added.length} only after`);
    for (const line of removed.slice(0, Number(options.limit))) console.log(`  - ${line.slice(0, 240)}`);
    for (const line of added.slice(0, Number(options.limit))) console.log(`  + ${line.slice(0, 240)}`);
    different ||= removed.length > 0 || added.length > 0;
}
process.exit(different ? 1 : 0);
