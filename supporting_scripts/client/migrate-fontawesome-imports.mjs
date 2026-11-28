/**
 * Rewrites the imports of FontAwesome icons to one module per icon: `import { faTrash } from '@fortawesome/free-solid-svg-icons/faTrash'`.
 *
 * A bundler that splits code per file (esbuild, which the Angular build uses) puts everything that a lazy route takes from the file with all icons
 * into the chunk that the entry bundle loads, so every icon that any route uses is part of the first download. An icon that is imported from its own
 * module stays out of the routes that do not use it. See supporting_scripts/client/fontawesome-icons.mjs and
 * documentation/docs/developer/guidelines/client-development.mdx.
 *
 * The icons come from the files of the installed packages, so the script keeps working when a package is updated. The types that the packages re-export
 * (`IconDefinition`, ...) are imported from `@fortawesome/fontawesome-svg-core`, where they are defined. Run it after a rebase or a merge that brought in an
 * import from the root of an icon package:
 *
 *   node supporting_scripts/client/migrate-fontawesome-imports.mjs [--check] [path ...]
 *
 * Without a path it handles `src/main/webapp` and `src/test`. `--check` changes nothing and exits with 1 if a file still imports an icon package root.
 * A statement with a name that this script cannot place stays as it is, and the script exits with 1. Run prettier on the changed files afterwards
 * (`pnpm exec prettier --write <files>`).
 */
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ICON_PACKAGES, iconNames } from './fontawesome-icons.mjs';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const require = createRequire(join(repoRoot, 'package.json'));
const ts = require('typescript');
const CORE = '@fortawesome/fontawesome-svg-core';
/** The types that the icon packages re-export from the core package. */
const CORE_TYPES = new Set(['IconDefinition', 'IconLookup', 'IconName', 'IconPack', 'IconPrefix']);

/** The module names of the icon packages that are imported from their root, with the names of the icons of each. */
const packages = new Map(
    ICON_PACKAGES.map((packageName) => [`@fortawesome/${packageName}`, new Set(iconNames(dirname(require.resolve(`@fortawesome/${packageName}/package.json`))))]),
);

function collectFiles(path, files = []) {
    const stats = statSync(path);
    if (stats.isFile()) {
        if (path.endsWith('.ts')) files.push(path);
        return files;
    }
    for (const entry of readdirSync(path, { withFileTypes: true })) {
        if (['node_modules', 'dist', 'build', 'coverage'].includes(entry.name)) continue;
        collectFiles(join(path, entry.name), files);
    }
    return files;
}

/**
 * The new text of the statements of one file, or the reason why a statement cannot be rewritten.
 *
 * @returns the edits (start, end, text) to apply to the text of the file, and the problems
 */
export function planRewrite(file, text) {
    const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
    const edits = [];
    const problems = [];
    const plans = [];
    let coreImport;

    for (const statement of source.statements) {
        const isImport = ts.isImportDeclaration(statement);
        const isExport = ts.isExportDeclaration(statement);
        if (!(isImport || isExport) || !statement.moduleSpecifier) continue;
        const specifier = statement.moduleSpecifier.text;
        if (isImport && specifier === CORE && statement.importClause?.namedBindings && ts.isNamedImports(statement.importClause.namedBindings) && !statement.importClause.name) {
            coreImport ??= statement;
        }
        const icons = packages.get(specifier);
        if (!icons) continue;

        const bindings = isImport ? statement.importClause?.namedBindings : statement.exportClause;
        if (!bindings || !(ts.isNamedImports(bindings) || ts.isNamedExports(bindings)) || (isImport && statement.importClause.name)) {
            problems.push(`${specifier}: a namespace, default or side-effect import cannot be split by icon`);
            continue;
        }
        const typeOnly = isImport ? statement.importClause.isTypeOnly : statement.isTypeOnly;
        const keyword = isImport ? `import${typeOnly ? ' type' : ''}` : `export${typeOnly ? ' type' : ''}`;
        const lines = [];
        const coreNames = [];
        let unmapped = false;
        for (const element of bindings.elements) {
            const name = (element.propertyName ?? element.name).text;
            if (icons.has(name)) {
                lines.push(`${keyword} { ${element.getText(source)} } from '${specifier}/${name}';`);
            } else if (isImport && CORE_TYPES.has(name)) {
                coreNames.push({ text: element.getText(source), local: element.name.text });
            } else {
                problems.push(`${specifier}: '${name}' is not an icon of the package`);
                unmapped = true;
            }
        }
        // A statement with a name that cannot be placed stays as it is: rewriting the rest would silently drop that name.
        if (unmapped) continue;
        plans.push({ statement, lines, coreNames });
    }

    // The types of all statements go into one import of the core package: into the one that the file has, or into the first rewritten statement.
    // The members are sorted by their local name, as the `sort-imports` rule of the client demands.
    const bySortedLocalName = (a, b) => (a.local < b.local ? -1 : a.local > b.local ? 1 : 0);
    const existing = coreImport ? coreImport.importClause.namedBindings.elements.map((element) => ({ text: element.getText(source), local: element.name.text })) : [];
    const present = new Set(existing.map((member) => member.text));
    const missingCoreNames = [...new Map(plans.flatMap((plan) => plan.coreNames).map((member) => [member.text, member])).values()].filter((member) => !present.has(member.text));
    if (missingCoreNames.length > 0 && coreImport) {
        const names = [...existing, ...missingCoreNames].sort(bySortedLocalName).map((member) => member.text);
        const keyword = coreImport.importClause.isTypeOnly ? 'import type' : 'import';
        edits.push({ start: coreImport.getStart(source), end: coreImport.getEnd(), text: `${keyword} { ${names.join(', ')} } from '${CORE}';` });
    } else if (missingCoreNames.length > 0 && plans.length > 0) {
        plans[0].lines.push(
            `import { ${missingCoreNames
                .sort(bySortedLocalName)
                .map((member) => member.text)
                .join(', ')} } from '${CORE}';`,
        );
    }

    for (const { statement, lines } of plans) {
        edits.push({ start: statement.getStart(source), end: statement.getEnd(), text: lines.join('\n') });
    }
    return { edits, problems };
}

/** The text with the edits applied. */
export function applyEdits(text, edits) {
    let output = text;
    for (const edit of [...edits].sort((a, b) => b.start - a.start)) {
        output = output.slice(0, edit.start) + edit.text + output.slice(edit.end);
    }
    return output;
}

function main() {
    const args = process.argv.slice(2);
    const check = args.includes('--check');
    const paths = args.filter((arg) => !arg.startsWith('--'));
    const targets = paths.length > 0 ? paths : [join(repoRoot, 'src/main/webapp'), join(repoRoot, 'src/test')];
    // A cheap filter before parsing, independent of the quote style: the parser decides whether a file really imports a package root.
    const packageNames = [...packages.keys()];

    let changedFiles = 0;
    const stillImporting = [];
    const problems = [];
    for (const file of targets.flatMap((target) => collectFiles(target))) {
        const text = readFileSync(file, 'utf8');
        if (!packageNames.some((name) => text.includes(name))) continue;
        const relative = file.startsWith(repoRoot) ? file.slice(repoRoot.length + 1) : file;
        const plan = planRewrite(file, text);
        problems.push(...plan.problems.map((problem) => `${relative}: ${problem}`));
        if (plan.edits.length === 0) continue;
        if (check) {
            stillImporting.push(relative);
            continue;
        }
        writeFileSync(file, applyEdits(text, plan.edits));
        changedFiles++;
    }

    for (const problem of problems) console.error(problem);
    if (check) {
        for (const file of stillImporting) console.error(`${file}: imports the root of an icon package; import each icon from its own module (run this script without --check)`);
        process.exit(stillImporting.length > 0 || problems.length > 0 ? 1 : 0);
    }
    console.log(`${changedFiles} files rewritten${problems.length > 0 ? `, ${problems.length} problems` : ''}`);
    process.exit(problems.length > 0 ? 1 : 0);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
    main();
}
