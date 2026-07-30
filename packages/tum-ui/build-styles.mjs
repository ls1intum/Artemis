import { readFile, rename, rm, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import chokidar from 'chokidar';
import postcss from 'postcss';
import tailwindcss from '@tailwindcss/postcss';

const packageRoot = dirname(fileURLToPath(import.meta.url));
const componentSourcePath = resolve(packageRoot, 'src');
const sourcePath = resolve(packageRoot, 'tailwind.css');
const outputPath = resolve(packageRoot, 'styles.css');
const watching = process.argv.includes('--watch');
const development = process.argv.includes('--development');
const hmrStyleDelay = 1250;

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

    const temporaryPath = `${outputPath}.${process.pid}.tmp`;
    try {
        await writeFile(temporaryPath, contents);
        await rename(temporaryPath, outputPath);
    } finally {
        await rm(temporaryPath, { force: true });
    }
    console.log(`Built ${outputPath}`);
}

async function build() {
    const source = await readFile(sourcePath, 'utf8');
    const result = await postcss([tailwindcss({ base: packageRoot, optimize: !development })]).process(source, { from: sourcePath });
    const stylesheet = postcss.parse(result.css);

    // Tailwind does not namespace its internal custom properties or keyframes when a class prefix is configured.
    const keyframes = new Map();
    stylesheet.walkAtRules((atRule) => {
        atRule.params = atRule.params.replaceAll('--tw-', '--tum-tw-');
        if (atRule.name.endsWith('keyframes') && !atRule.params.startsWith('tum-')) {
            keyframes.set(atRule.params, `tum-${atRule.params}`);
            atRule.params = `tum-${atRule.params}`;
        }
    });
    stylesheet.walkDecls((declaration) => {
        declaration.prop = declaration.prop.replaceAll('--tw-', '--tum-tw-');
        declaration.value = declaration.value.replaceAll('--tw-', '--tum-tw-');
        for (const [original, namespaced] of keyframes) {
            declaration.value = declaration.value.replace(new RegExp(`(?<![-\\\\w])${original.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&')}(?![-\\\\w])`, 'g'), namespaced);
        }
    });

    await writeIfChanged(outputPath, stylesheet.toString());
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
        // Separate component and generated-style changes so Angular can hot-update both without reloading the page.
        rebuildTimer = setTimeout(() => void rebuild(), hmrStyleDelay);
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
