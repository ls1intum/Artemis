import { mkdir, readFile, watch, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import postcss from 'postcss';
import tailwindcss from '@tailwindcss/postcss';

const packageRoot = dirname(fileURLToPath(import.meta.url));
const sourcePath = resolve(packageRoot, 'tailwind.css');
const outputArgument = process.argv.indexOf('--output');
const outputPath = outputArgument >= 0 ? resolve(process.argv[outputArgument + 1]) : resolve(packageRoot, '../../dist/tum-ui/styles.css');
const watching = process.argv.includes('--watch');

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

    await mkdir(dirname(outputPath), { recursive: true });
    await writeFile(outputPath, stylesheet.toString());
    console.log(`Built ${outputPath}`);
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

    for await (const event of watch(packageRoot, { recursive: true })) {
        const relativePath = event.filename?.replaceAll('\\', '/');
        if (
            !relativePath ||
            (relativePath !== 'tailwind.css' && !relativePath.startsWith('src/')) ||
            relativePath.endsWith('.spec.ts') ||
            relativePath.endsWith('.stories.ts') ||
            (!relativePath.endsWith('.html') && !relativePath.endsWith('.ts') && !relativePath.endsWith('.css') && !relativePath.endsWith('.scss'))
        ) {
            continue;
        }
        clearTimeout(rebuildTimer);
        rebuildTimer = setTimeout(() => void rebuild(), 50);
    }
}
