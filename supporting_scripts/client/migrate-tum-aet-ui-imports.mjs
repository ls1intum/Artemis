/**
 * Rewrites imports of `@tumaet/ui-angular` to the entry point of each component: `@tumaet/ui-angular/button`, `@tumaet/ui-angular/dialog`, ...
 *
 * A bundler that splits code per file (esbuild, which the Angular build uses) puts everything that a lazy route takes from the package root into the
 * chunk that the entry bundle loads. An entry per component keeps the code of a component out of the routes that do not use it. See
 * documentation/docs/developer/guidelines/tum-aet-ui-kit.mdx.
 *
 * The entry point of a symbol is read from the `exports` of the package and the entry files they name (`packages/tum-aet-ui/src/lib/<directory>/index.ts`),
 * so the script keeps working when a component is added. Run it after a rebase or a merge that brought in an import from the package root:
 *
 *   node supporting_scripts/client/migrate-tum-aet-ui-imports.mjs [--check] [path ...]
 *
 * Without a path it handles `src/main/webapp` and `src/test`. `--check` changes nothing and exits with 1 if a file still imports the package root.
 * Run prettier on the changed files afterwards (`pnpm exec prettier --write <files>`).
 */
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const ts = createRequire(join(repoRoot, 'package.json'))('typescript');
const PACKAGE = '@tumaet/ui-angular';

/** The entry point of every exported symbol: the `exports` of the package name the entry points, and the `index.ts` of each lists its symbols. */
function loadSymbolEntries() {
    const exportsOfPackage = JSON.parse(readFileSync(join(repoRoot, 'packages/tum-aet-ui/package.json'), 'utf8')).exports;
    const entryOf = new Map();
    for (const [subpath, target] of Object.entries(exportsOfPackage)) {
        if (subpath === '.' || !target.endsWith('/index.ts')) continue;
        const file = join(repoRoot, 'packages/tum-aet-ui', target);
        const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true);
        for (const statement of source.statements) {
            if (ts.isExportDeclaration(statement) && statement.exportClause && ts.isNamedExports(statement.exportClause)) {
                for (const element of statement.exportClause.elements) {
                    entryOf.set(element.name.text, subpath.slice(2));
                }
            }
        }
    }
    return entryOf;
}

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

const args = process.argv.slice(2);
const check = args.includes('--check');
const paths = args.filter((a) => !a.startsWith('--')).map((p) => resolve(p));
const targets = paths.length > 0 ? paths : [join(repoRoot, 'src/main/webapp'), join(repoRoot, 'src/test')];
const entryOf = loadSymbolEntries();

let changedFiles = 0;
const stillImporting = [];
const problems = [];

for (const file of targets.flatMap((target) => collectFiles(target))) {
    const text = readFileSync(file, 'utf8');
    if (!text.includes(`'${PACKAGE}'`) && !text.includes(`"${PACKAGE}"`)) continue;
    const source = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
    const edits = [];

    for (const statement of source.statements) {
        const isImport = ts.isImportDeclaration(statement);
        const isExport = ts.isExportDeclaration(statement);
        if (!(isImport || isExport) || !statement.moduleSpecifier || statement.moduleSpecifier.text !== PACKAGE) continue;
        const bindings = isImport ? statement.importClause?.namedBindings : statement.exportClause;
        const relative = file.slice(repoRoot.length + 1);
        if (!bindings || !(ts.isNamedImports(bindings) || ts.isNamedExports(bindings)) || (isImport && statement.importClause.name)) {
            problems.push(`${relative}: a namespace, default or side-effect import of ${PACKAGE} cannot be split by entry point`);
            continue;
        }
        const byEntry = new Map();
        let unmapped = false;
        for (const element of bindings.elements) {
            const imported = (element.propertyName ?? element.name).text;
            const entry = entryOf.get(imported);
            if (!entry) {
                problems.push(`${relative}: '${imported}' is not exported by an entry point of ${PACKAGE}`);
                unmapped = true;
                continue;
            }
            (byEntry.get(entry) ?? byEntry.set(entry, []).get(entry)).push(element.getText(source));
        }
        // A statement with a name that no entry point exports stays as it is: rewriting the rest would silently drop that name.
        if (unmapped || byEntry.size === 0) continue;
        const typeOnly = isImport ? statement.importClause.isTypeOnly : statement.isTypeOnly;
        const keyword = isImport ? `import${typeOnly ? ' type' : ''}` : `export${typeOnly ? ' type' : ''}`;
        const rewritten = [...byEntry.keys()]
            .sort()
            .map((entry) => `${keyword} { ${byEntry.get(entry).join(', ')} } from '${PACKAGE}/${entry}';`)
            .join('\n');
        edits.push({ start: statement.getStart(source), end: statement.getEnd(), text: rewritten });
    }

    if (edits.length === 0) continue;
    if (check) {
        stillImporting.push(file.slice(repoRoot.length + 1));
        continue;
    }
    let output = text;
    for (const edit of edits.sort((a, b) => b.start - a.start)) {
        output = output.slice(0, edit.start) + edit.text + output.slice(edit.end);
    }
    writeFileSync(file, output);
    changedFiles++;
}

for (const problem of problems) console.error(problem);
if (check) {
    for (const file of stillImporting) console.error(`${file}: imports ${PACKAGE}; import from the entry point of the component (run this script without --check)`);
    process.exit(stillImporting.length > 0 || problems.length > 0 ? 1 : 0);
}
console.log(`${changedFiles} files rewritten${problems.length > 0 ? `, ${problems.length} problems` : ''}`);
process.exit(problems.length > 0 ? 1 : 0);
