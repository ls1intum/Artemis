import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import chokidar from 'chokidar';
import postcss from 'postcss';
import tailwindcss from '@tailwindcss/postcss';

const packageRoot = dirname(fileURLToPath(import.meta.url));
const componentSourcePath = resolve(packageRoot, 'src');
const sourcePath = resolve(packageRoot, 'tailwind.css');

function parseOutputPaths(arguments_) {
    const paths = [];
    for (let index = 0; index < arguments_.length; index++) {
        if (arguments_[index] !== '--output') {
            continue;
        }
        const outputPath = arguments_[++index];
        if (!outputPath || outputPath.startsWith('--')) {
            throw new Error('--output requires a path');
        }
        paths.push(resolve(outputPath));
    }
    return paths;
}

const outputPaths = parseOutputPaths(process.argv);
if (outputPaths.length === 0) {
    outputPaths.push(resolve(packageRoot, '../../dist/tum-ui/styles.css'));
}
const watching = process.argv.includes('--watch');

async function writeIfChanged(outputPath, contents) {
    try {
        if ((await readFile(outputPath, 'utf8')) === contents) {
            return;
        }
    } catch (error) {
        if (error.code !== 'ENOENT') {
            throw error;
        }
    }

    await mkdir(dirname(outputPath), { recursive: true });
    await writeFile(outputPath, contents);
    console.log(`Built ${outputPath}`);
}

async function build() {
    const source = await readFile(sourcePath, 'utf8');
    const result = await postcss([tailwindcss({ base: packageRoot, optimize: !watching })]).process(source, { from: sourcePath });
    const stylesheet = postcss.parse(result.css);

    // Keep component utilities above lower-specificity host resets loaded before the package stylesheet.
    stylesheet.walkAtRules('layer', (layer) => {
        if (layer.nodes) {
            layer.replaceWith(...layer.nodes);
        } else {
            layer.remove();
        }
    });
    // Tailwind does not namespace its internal custom properties or keyframes when a class prefix is configured.
    stylesheet.walkDecls((declaration) => {
        declaration.prop = declaration.prop.replaceAll('--tw-', '--tum-tw-');
        declaration.value = declaration.value.replaceAll('--tw-', '--tum-tw-');
        if (declaration.prop === '--tum-animate-spin') {
            declaration.value = declaration.value.replace(/\bspin\b/g, 'tum-spin');
        }
    });
    stylesheet.walkAtRules((atRule) => {
        atRule.params = atRule.params.replaceAll('--tw-', '--tum-tw-');
        if (atRule.name.endsWith('keyframes') && atRule.params === 'spin') {
            atRule.params = 'tum-spin';
        }
    });

    await Promise.all(outputPaths.map((outputPath) => writeIfChanged(outputPath, stylesheet.toString())));
}

await build();

if (watching) {
    let rebuildTimer;
    let rebuilding = false;
    let rebuildQueued = false;

    async function rebuild() {
        if (rebuilding) {
            rebuildQueued = true;
            return;
        }
        rebuilding = true;
        do {
            rebuildQueued = false;
            try {
                await build();
            } catch (error) {
                console.error(error);
            }
        } while (rebuildQueued);
        rebuilding = false;
    }

    function scheduleRebuild() {
        clearTimeout(rebuildTimer);
        rebuildTimer = setTimeout(() => void rebuild(), 50);
    }

    chokidar
        .watch([componentSourcePath, sourcePath], {
            ignoreInitial: true,
            ignored: (path, stats) =>
                stats?.isFile() &&
                (path.endsWith('.spec.ts') ||
                    path.endsWith('.stories.ts') ||
                    (!path.endsWith('.html') && !path.endsWith('.ts') && !path.endsWith('.css') && !path.endsWith('.scss'))),
        })
        .on('all', scheduleRebuild);
}
