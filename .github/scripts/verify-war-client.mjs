/**
 * Verifies the Angular entry point that the deployable WAR serves.
 *
 * The check reads the main script that `index.html` references, requires exactly one archive entry for it and applies a minification heuristic
 * to that entry. It does not look at other `main-*.js` entries: a minified bundle that nothing references must not hide an unminified entry point.
 *
 *   node .github/scripts/verify-war-client.mjs <path to Artemis.war>
 *
 * The script runs in the `Build .war artifact` job, which has installed the dependencies of the workspace.
 */
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { unzipSync } from 'fflate';
import { parse } from 'parse5';

const STATIC = 'WEB-INF/classes/static/';
const INDEX = `${STATIC}index.html`;
const MAIN_SOURCE = /^(?:\/|\.\/)?(main-[a-zA-Z0-9_-]+\.js)(?:[?#].*)?$/;
// An optimized main bundle averages several hundred bytes per line and an unoptimized one about 60, so this separates them with a wide margin.
export const MIN_AVERAGE_LINE_BYTES = 200;

/** The `src` of every `<script>` of the HTML that names a `main-*.js` file (comments are not scripts). */
export function referencedMainScripts(html) {
    const bundles = [];
    const visit = (node) => {
        if (node.tagName === 'script') {
            const source = node.attrs.find((attribute) => attribute.name === 'src')?.value ?? '';
            const match = MAIN_SOURCE.exec(source);
            if (match) {
                bundles.push(match[1]);
            }
        }
        // the content of a <template> is a separate fragment that childNodes does not contain
        for (const child of [...(node.childNodes ?? []), ...(node.content ? [node.content] : [])]) {
            visit(child);
        }
    };
    visit(parse(html));
    return bundles;
}

/** The number of lines of the content with the line terminators `\n`, `\r\n` and `\r`; a last line without a terminator counts as well. */
function countLines(content) {
    const lines = Buffer.from(content)
        .toString('latin1')
        .split(/\r\n|\r|\n/);
    if (lines[lines.length - 1] === '') {
        lines.pop();
    }
    return lines.length;
}

/** Reads the entries that the check needs without inflating the rest of the archive, and counts the entries of each name. */
function readArchive(war) {
    const entryCounts = new Map();
    const files = unzipSync(readFileSync(war), {
        filter: ({ name }) => {
            entryCounts.set(name, (entryCounts.get(name) ?? 0) + 1);
            return name === INDEX || (name.startsWith(STATIC) && name.endsWith('.js') && /\/main-[^/]+$/.test(name));
        },
    });
    return { files, entryCounts };
}

/** Throws an `Error` that names the problem when the WAR does not serve a minified Angular client. */
export function verifyWar(war) {
    const { files, entryCounts } = readArchive(war);
    if (entryCounts.get(INDEX) !== 1) {
        throw new Error(`Expected exactly one ${INDEX} in the WAR`);
    }

    const bundles = referencedMainScripts(Buffer.from(files[INDEX]).toString('utf8'));
    if (bundles.length !== 1) {
        throw new Error(`Expected index.html to reference exactly one main-*.js script, found ${bundles.length}`);
    }

    const bundle = STATIC + bundles[0];
    if (entryCounts.get(bundle) !== 1) {
        throw new Error(`Expected exactly one entry for the main script referenced by index.html (${bundle})`);
    }
    const content = files[bundle];

    const lines = countLines(content);
    if (lines === 0 || content.length < MIN_AVERAGE_LINE_BYTES * lines) {
        throw new Error(`${bundles[0]} is not minified (${content.length} bytes in ${lines} lines). Build the client with 'pnpm run webapp:prod'.`);
    }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    const war = process.argv[2];
    if (!war) {
        console.error('usage: node .github/scripts/verify-war-client.mjs <path to Artemis.war>');
        process.exit(2);
    }
    try {
        verifyWar(war);
    } catch (error) {
        console.log(`::error::${war}: ${error.message}`);
        process.exit(1);
    }
}
