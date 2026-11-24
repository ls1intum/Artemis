/**
 * This script is executed before the Angular CLI's build script is executed.
 * It adds environment variables to the environment file and merges the i18n files.
 * This way, we don't need a webpack configuration file: It replaces
 * - webpack.DefinePlugin and
 * - MergeJsonWebpackPlugin
 */
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { hashElement } from 'folder-hash';
import { fileURLToPath } from 'url';
import { spawn } from 'node:child_process';
import * as esbuild from 'esbuild';
import { runViteOverrideCheck } from '../check-vite-override.mjs';

// Runs on every `ng serve` and every client build: a vite version that does not match what
// @angular/build expects breaks the dev server silently (blank page), so fail here instead.
runViteOverrideCheck();

const __filename = fileURLToPath(import.meta.url);
const repoRoot = path.resolve(path.dirname(__filename), '../..');
process.chdir(repoRoot);
const languagesHash = await hashElement(path.resolve(repoRoot, 'src', 'main', 'webapp', 'i18n'), {
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
fs.writeFileSync(path.resolve(repoRoot, 'src', 'main', 'webapp', 'app', 'core', 'environments', 'environment.override.ts'), environmentConfig);

// =====================
// i18n merging
// =====================

const groups = [
    { folder: './src/main/webapp/i18n/en', output: './src/main/webapp/i18n/en.json', landingOutput: './src/main/webapp/i18n/en-landing.json' },
    { folder: './src/main/webapp/i18n/de', output: './src/main/webapp/i18n/de.json', landingOutput: './src/main/webapp/i18n/de-landing.json' },
];

// The landing and the sign-in page need `landing` (components), `global` (menu/ribbon, footer), `home` (sign-in form) and `login`
// (sign-in errors), and the few keys of other namespaces that the sign-in page, its alerts and its passkey flow show. An entry is a
// namespace or a path into one (`error.http`): `artemisApp` alone is most of the catalog. Keep this list narrow: shipping extra keys here bloats
// the initial i18n request that blocks LCP. A key that is missing here shows up as `translation-not-found[...]` until the full
// catalog has loaded, so add the keys of a component that the sign-in page shows.
// `SplitTranslateLoader` (app/core/config/split-translate-loader.ts) decides which routes start from this slice.
const LANDING_NAMESPACES = [
    'landing',
    'global',
    'home',
    'login',
    'entity.action',
    'error.http',
    'artemisApp.alerts',
    'artemisApp.error',
    'artemisApp.git',
    'artemisApp.legal.imprint.title',
    'artemisApp.legal.privacy',
    'artemisApp.lti.ltiSuccessLoginRequired',
    'artemisApp.outdatedAction',
    'artemisApp.outdatedAlert',
    'artemisApp.userSettings.passkeySettingsPage.error',
];

const isObject = (obj) => obj && typeof obj === 'object';

/** Returns an object that holds only the value at the dotted path `a.b.c` of `source` (as `{ a: { b: { c: value } } }`), or undefined if there is none. */
function pick(source, dottedPath) {
    const segments = dottedPath.split('.');
    const value = segments.reduce((current, segment) => (isObject(current) ? current[segment] : undefined), source);
    if (value === undefined) {
        return undefined;
    }
    return segments.reduceRight((nested, segment) => ({ [segment]: nested }), value);
}

function deepMerge(target, source) {
    if (!isObject(target) || !isObject(source)) {
        return source;
    }

    for (const key in source) {
        // prevent prototype pollution; use Object.prototype.hasOwnProperty.call so a JSON-supplied
        // "hasOwnProperty" key can't shadow the method and break the check.
        if (!Object.prototype.hasOwnProperty.call(source, key)) continue;
        if (key === '__proto__' || key === 'constructor') continue;

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

        const files = fs.readdirSync(group.folder).filter((file) => file.endsWith('.json'));

        const mergedContent = files.reduce((acc, file) => {
            const content = JSON.parse(fs.readFileSync(path.resolve(group.folder, file)).toString());
            return deepMerge(acc, content);
        }, {});

        await fs.promises.writeFile(group.output, JSON.stringify(mergedContent));

        // Emit a landing-and-sign-in slice so these routes can hydrate i18n without the whole catalog.
        const landingContent = LANDING_NAMESPACES.reduce((acc, entry) => {
            const picked = pick(mergedContent, entry);
            return picked === undefined ? acc : deepMerge(acc, picked);
        }, {});
        await fs.promises.writeFile(group.landingOutput, JSON.stringify(landingContent));
    } catch (error) {
        console.error(`Error merging JSON files for ${group.output}:`, error);
    }
}

// ==================================================
// Vendor stylesheets that embed their fonts
// ==================================================

/*
 * Apollon (its Inter faces) and the Monaco editor (its codicon font) ship their stylesheets with the fonts embedded as base64 data URIs.
 * Both stylesheets are global, so every page would download all of these fonts, the sign-in page included, although almost no page uses
 * them. This step writes the fonts to files next to a copy of each stylesheet and references them by URL (`angular.json` lists the
 * copies). The rules stay as they are and global, and the browser fetches a font only when a glyph needs it. The generated files are
 * ignored by git, like the merged i18n files above.
 */
// Not below `content/`: `angular.json` copies that directory to the output as it is, which would ship every font twice.
const VENDOR_STYLESHEET_DIRECTORY = './src/main/webapp/vendor-styles';
const VENDOR_STYLESHEETS = [
    { source: './node_modules/@tumaet/apollon/dist/assets/style.css', output: 'apollon.css' },
    { source: './node_modules/monaco-editor/min/vs/editor/editor.main.css', output: 'monaco-editor.css' },
];
const FONT_EXTENSIONS = { 'font/woff2': 'woff2', 'font/woff': 'woff', 'font/ttf': 'ttf', 'font/otf': 'otf' };
const EMBEDDED_FONT = /url\((['"]?)data:(font\/(?:woff2|woff|ttf|otf));base64,([A-Za-z0-9+/=]+)\1\)/g;

fs.rmSync(VENDOR_STYLESHEET_DIRECTORY, { recursive: true, force: true });
fs.mkdirSync(path.join(VENDOR_STYLESHEET_DIRECTORY, 'fonts'), { recursive: true });
for (const stylesheet of VENDOR_STYLESHEETS) {
    const css = fs.readFileSync(stylesheet.source, 'utf8');
    let fontCount = 0;
    const externalized = css.replace(EMBEDDED_FONT, (_match, _quote, mimeType, base64) => {
        const font = Buffer.from(base64, 'base64');
        // Named after the content, so a font that two stylesheets share is written once and a changed font gets a new name.
        const fileName = `${crypto.createHash('sha1').update(font).digest('hex').slice(0, 12)}.${FONT_EXTENSIONS[mimeType]}`;
        fs.writeFileSync(path.join(VENDOR_STYLESHEET_DIRECTORY, 'fonts', fileName), font);
        fontCount++;
        return `url(./fonts/${fileName})`;
    });
    if (fontCount === 0) {
        // A stylesheet without embedded fonts, or with another encoding of them, makes this step obsolete or wrong: it must not pass silently.
        throw new Error(`${stylesheet.source} embeds no fonts as data URIs. Remove it from VENDOR_STYLESHEETS in supporting_scripts/client/prebuild.mjs.`);
    }
    fs.writeFileSync(path.join(VENDOR_STYLESHEET_DIRECTORY, stylesheet.output), externalized);
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
    'vs/editor/editor.worker.js',
];
await esbuild.build({
    entryPoints: workerEntryPoints.map((entry) => `node_modules/monaco-editor/esm/${entry}`),
    bundle: true,
    format: 'esm',
    outbase: 'node_modules/monaco-editor/esm',
    outdir: 'node_modules/monaco-editor/bundles',
});

await new Promise((resolve, reject) => {
    const buildScript = `tum-aet-ui:build${developFlag ? ':dev' : ''}`;
    const command = process.platform === 'win32' ? 'cmd.exe' : 'pnpm';
    const commandArguments = process.platform === 'win32' ? ['/d', '/s', '/c', `pnpm run ${buildScript}`] : ['run', buildScript];
    const child = spawn(command, commandArguments, {
        cwd: repoRoot,
        stdio: 'inherit',
    });
    child.once('error', reject);
    child.once('exit', (code, signal) => {
        if (code === 0) {
            resolve();
            return;
        }
        reject(new Error(`TUM AET UI package build failed${signal ? ` with signal ${signal}` : ` with exit code ${code}`}.`));
    });
});

console.log('Pre-Build complete!');
