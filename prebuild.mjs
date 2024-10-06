/**
 * This script is executed before the Angular CLI's build script is executed.
 * It adds environment variables to the environment file and merges the i18n files.
 * This way, we don't need a webpack configuration file: It replaces
 * - webpack.DefinePlugin and
 * - MergeJsonWebpackPlugin
 */
import fs from 'fs';
import path from 'path';
import { hashElement } from 'folder-hash';
import { fileURLToPath } from 'url';
import * as esbuild from 'esbuild';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const languagesHash = await hashElement(path.resolve(__dirname, 'src', 'main', 'webapp', 'i18n'), {
    algo: 'md5',
    encoding: 'hex',
    files: { include: ['*.json'] },
});

// =====================
// Environment variables
// =====================

/*
 * Needed for client compilations with docker compose, where the 'APP_VERSION' property isn't injected by gradle.
 *
 * Returns the inferred APP_VERSION from 'build.gradle', or 'DEV' if this couldn't be retrieved
 */
function inferVersion() {
    let version = 'DEV';

    try {
        let data = fs.readFileSync('build.gradle', 'UTF-8');

        version = data.match(/\nversion\s=\s"(.*)"/);

        version = version[1] ?? 'DEV';
    } catch (error) {
        console.log("Error while retrieving 'APP_VERSION' property");
    }

    return version;
}

// --develop flag is used to enable debug mode
const args = process.argv.slice(2);
const developFlag = args.includes('--develop');
const environmentConfig = `// Don't change this file manually, it will be overwritten by the build process!
export const __DEBUG_INFO_ENABLED__ = ${developFlag};
export const __VERSION__ = '${process.env.APP_VERSION || inferVersion()}';
// The root URL for API calls, ending with a '/' - for example: \`"https://www.jhipster.tech:8081/myservice/"\`.
// If you use an API server, in \`prod\` mode, you will need to enable CORS
// (see the \`jhipster.cors\` common JHipster property in the \`application-*.yml\` configurations)
export const I18N_HASH = '${languagesHash.hash}';
`;
fs.writeFileSync(path.resolve(__dirname, 'src', 'main', 'webapp', 'app', 'environments', 'environment.override.ts'), environmentConfig);


// =====================
// i18n merging
// =====================


const groups = [
    { folder: './src/main/webapp/i18n/en', output: './src/main/webapp/i18n/en.json' },
    { folder: './src/main/webapp/i18n/de', output: './src/main/webapp/i18n/de.json' },
];

const isObject = (obj) => obj && typeof obj === 'object';

function deepMerge(target, source) {
    if (!isObject(target) || !isObject(source)) {
        return source;
    }

    for (const key in source) {
        // prevent prototype pollution
        if (!source.hasOwnProperty(key)) continue;
        if (key === "__proto__" || key === "constructor") continue;

        const targetValue = target[key];
        const sourceValue = source[key];

        if (isObject(sourceValue)) {
            target[key] = deepMerge(targetValue || {}, sourceValue);
        } else {
            target[key] = sourceValue;
        }
    }

    return target;
}


for (const group of groups) {
    try {
        // create output folder if it doesn't exist
        fs.mkdirSync(path.dirname(group.output), { recursive: true });

        const files = fs.readdirSync(group.folder).filter(file => file.endsWith('.json'));

        const mergedContent = files.reduce((acc, file) => {
            const content = JSON.parse(fs.readFileSync(path.resolve(group.folder, file)).toString());
            return deepMerge(acc, content);
        }, {});

        await fs.promises.writeFile(group.output, JSON.stringify(mergedContent));
    } catch (error) {
        console.error(`Error merging JSON files for ${group.output}:`, error);
    }
}

/*
 * The workers of the monaco editor must be bundled separately.
 * Specialized workers are available in the vs/esm/language/ directory.
 * Be sure to modify the MonacoConfig if you choose to add a worker here.
 * For more details, refer to https://github.com/microsoft/monaco-editor/blob/main/samples/browser-esm-esbuild/build.js
 */
const workerEntryPoints = [
    'vs/language/json/json.worker.js',
    'vs/language/css/css.worker.js',
    'vs/language/html/html.worker.js',
    'vs/language/typescript/ts.worker.js',
    'vs/editor/editor.worker.js'
];
await esbuild.build({
    entryPoints: workerEntryPoints.map((entry) => `node_modules/monaco-editor/esm/${entry}`),
    bundle: true,
    format: 'iife',
    outbase: 'node_modules/monaco-editor/esm',
    outdir: 'node_modules/monaco-editor/bundles'
});

console.log("Pre-Build complete!");
