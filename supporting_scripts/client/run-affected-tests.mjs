import { execFileSync, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const appRoot = 'src/main/webapp/app/';
const integrationSpec = /^src\/test\/javascript\/spec\/integration\/(?:code-editor|monaco-editor)\/.*\.spec\.ts$/;
const resourceExtension = /\.(?:html|scss|css)$/;
const infrastructure =
    /^(?:package\.json$|pnpm-(?:lock|workspace)\.yaml$|\.npmrc$|angular\.json$|tsconfig[^/]*\.json$|vite(?:st)?[^/]*\.[cm]?[jt]s$|config\/|packages\/|supporting_scripts\/|\.github\/)/;
const sharedSource = /^(?:shared\/|foundation\/|shared-ui\/|openapi\/|core\/(?:config|environments|util|global)\/|[^/]+$)/;
// Match ci.yml's client_tests exclusions. Every other path is relevant by default: prebuild and
// custom rule tests also consume global inputs such as .nvmrc, build.gradle and gradle/.
const nonClientPrefixes = ['documentation/', 'src/main/java/', 'src/test/java/', 'src/main/resources/', 'src/test/resources/'];
const configs = ['vitest.config.ts', 'config/testing/vitest.monaco.config.ts'];
const fullPlan = (reason) => ({ mode: 'full', reason });

function clientRelevant(file) {
    return !file.endsWith('.md') && file !== 'LICENSE' && !nonClientPrefixes.some((prefix) => file.startsWith(prefix));
}

function git(root, args) {
    return execFileSync('git', args, { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], maxBuffer: 16 * 1024 * 1024 });
}

/** The merge base excludes changes made only on the target branch. NUL records preserve all valid Git paths. */
export function readChanges(root, baseRef) {
    const base = git(root, ['rev-parse', '--verify', '--end-of-options', `${baseRef}^{commit}`]).trim();
    const mergeBase = git(root, ['merge-base', base, 'HEAD']).trim();
    const records = git(root, ['diff', '--name-status', '-z', '--find-renames', mergeBase, '--']).split('\0');
    const changes = [];
    for (let index = 0; index < records.length - 1;) {
        const status = records[index++];
        const paths = records.slice(index, index + (/^[RC]/.test(status) ? 2 : 1));
        index += paths.length;
        if (paths.some((file) => !file)) throw new Error('Incomplete Git name-status record');
        changes.push({ status, paths });
    }
    for (const file of git(root, ['ls-files', '--others', '--exclude-standard', '-z']).split('\0').filter(Boolean)) {
        changes.push({ status: 'A', paths: [file] });
    }
    return changes;
}

function addImporter(importers, resource, importer) {
    const consumers = importers.get(resource) ?? new Set();
    consumers.add(importer);
    importers.set(resource, consumers);
}

function resourcePath(root, importer, reference) {
    if (reference.startsWith('app/')) reference = `/${appRoot}${reference.slice(4)}`;
    if (reference.startsWith('src/')) reference = `/${reference}`;
    const base = reference.startsWith('/') ? root : path.dirname(path.join(root, importer));
    return path
        .relative(root, path.resolve(base, reference.replace(/^\//, '').replace(/\?.*$/, '')))
        .split(path.sep)
        .join('/');
}

function sassImports(root, file, source, files, importers) {
    const directives = [...source.matchAll(/@(?:use|forward|import)\s+([^;]+);/g)];
    if (directives.length !== [...source.matchAll(/@(?:use|forward|import)\b/g)].length || /\bload-css\s*\(/.test(source)) {
        throw new Error(`Unsupported Sass resource import in ${file}`);
    }
    for (const directive of directives) {
        const body = directive[1];
        if (body.includes('#{') || body.includes('\\') || !/^\s*['"]/.test(body) || (directive[0].startsWith('@import') && /\$|\burl\(/.test(body))) {
            throw new Error(`Dynamic or unsupported Sass resource import in ${file}`);
        }
        for (const literal of body.matchAll(/(['"])([^'"]+)\1/g)) {
            const reference = literal[2];
            if (/^(?:sass:|https?:|\/\/|data:)/.test(reference)) continue;
            for (const base of [path.dirname(path.join(root, file)), root]) {
                const absolute = path.resolve(base, reference);
                const directory = path.dirname(absolute);
                const basename = path.basename(absolute);
                // Sass accepts omitted extensions, underscored partials, and directory index files.
                const candidates = [absolute, path.join(directory, `_${basename}`)];
                if (!/\.(?:css|scss)$/.test(reference)) {
                    for (const extension of ['scss', 'css']) {
                        candidates.push(`${absolute}.${extension}`, path.join(directory, `_${basename}.${extension}`));
                        candidates.push(path.join(absolute, `index.${extension}`), path.join(absolute, `_index.${extension}`));
                    }
                }
                for (const candidate of candidates) {
                    const relative = path.relative(root, candidate).split(path.sep).join('/');
                    if (files.has(relative)) addImporter(importers, relative, file);
                }
            }
        }
    }
}

async function resourceOwners(root, changedResources, files) {
    const ts = await import('typescript');
    const importers = new Map();
    const knownFiles = new Set(files);
    for (const file of files) {
        if (!file.startsWith(appRoot) && !integrationSpec.test(file)) continue;
        if (!/\.(?:ts|scss|css)$/.test(file)) continue;
        const source = fs.readFileSync(path.join(root, file), 'utf8');
        if (/\.(?:scss|css)$/.test(file)) {
            sassImports(root, file, source, knownFiles, importers);
            continue;
        }
        const syntax = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true);
        if (syntax.parseDiagnostics.length) throw new Error(`Cannot parse resource metadata in ${file}`);
        const componentNames = new Set(['Component']);
        for (const statement of syntax.statements) {
            if (!ts.isImportDeclaration(statement) || statement.moduleSpecifier.text !== '@angular/core') continue;
            const bindings = statement.importClause?.namedBindings;
            if (!bindings || !ts.isNamedImports(bindings)) continue;
            for (const binding of bindings.elements) {
                if ((binding.propertyName ?? binding.name).text === 'Component') componentNames.add(binding.name.text);
            }
        }
        const visit = (node) => {
            if (ts.isCallExpression(node) && (componentNames.has(node.expression.getText(syntax)) || /\.Component$/.test(node.expression.getText(syntax)))) {
                const metadata = node.arguments[0];
                if (!metadata || !ts.isObjectLiteralExpression(metadata) || metadata.properties.some(ts.isSpreadAssignment)) {
                    throw new Error(`Dynamic component resource metadata in ${file}`);
                }
                for (const property of metadata.properties) {
                    const name = property.name?.getText(syntax).replace(/^['"]|['"]$/g, '');
                    if (!['templateUrl', 'styleUrl', 'styleUrls', 'styles'].includes(name)) continue;
                    if (!ts.isPropertyAssignment(property)) throw new Error(`Dynamic component resource metadata in ${file}`);
                    const value = property.initializer;
                    const values = ts.isArrayLiteralExpression(value) ? value.elements : [value];
                    for (const item of values) {
                        if (!ts.isStringLiteralLike(item)) throw new Error(`Dynamic component resource metadata in ${file}`);
                        if (name === 'styles') sassImports(root, file, item.text, knownFiles, importers);
                        else addImporter(importers, resourcePath(root, file, item.text), file);
                    }
                }
            }
            if (ts.isImportDeclaration(node) && ts.isStringLiteralLike(node.moduleSpecifier)) {
                const reference = node.moduleSpecifier.text;
                if (/\.(?:html|scss|css)(?:\?.*)?$/.test(reference)) addImporter(importers, resourcePath(root, file, reference), file);
            }
            ts.forEachChild(node, visit);
        };
        visit(syntax);
    }
    const owners = new Set();
    for (const resource of changedResources) {
        const queue = [resource];
        const seen = new Set();
        const resourceSources = new Set();
        for (const item of queue) {
            if (seen.has(item)) continue;
            seen.add(item);
            for (const importer of importers.get(item) ?? []) {
                if (importer.endsWith('.ts')) resourceSources.add(importer);
                else queue.push(importer);
            }
        }
        if (!resourceSources.size) throw new Error(`No static resource owner found for ${resource}`);
        for (const source of resourceSources) owners.add(source);
    }
    return owners;
}

/** Expand Angular/Sass resources before Vitest walks transitive TypeScript imports. */
export async function planTests({ root, changes, files }) {
    const sources = new Set();
    const resources = [];
    for (const { status, paths } of changes) {
        if (!paths.some(clientRelevant)) continue;
        if (!['M', 'A'].includes(status)) return fullPlan(`Conservative fallback for ${status}: ${paths.join(' -> ')}`);
        const file = paths[0];
        if (infrastructure.test(file) || (file.startsWith(appRoot) && sharedSource.test(file.slice(appRoot.length)))) {
            return fullPlan(`Shared client infrastructure changed: ${file}`);
        }
        if (integrationSpec.test(file) || (file.startsWith(appRoot) && file.endsWith('.ts'))) sources.add(file);
        else if (file.startsWith(appRoot) && resourceExtension.test(file)) resources.push(file);
        else return fullPlan(`Unsupported client input changed: ${file}`);
    }
    if (resources.length) {
        try {
            for (const owner of await resourceOwners(root, resources, files)) sources.add(owner);
        } catch (error) {
            return fullPlan(`Conservative resource fallback: ${error.message}`);
        }
    }
    return sources.size
        ? { mode: 'affected', sources: [...sources].sort(), reason: 'Changed client sources and resource owners' }
        : { mode: 'none', reason: 'No client inputs changed' };
}

function runCommand(args, root) {
    const child = spawnSync('pnpm', args, { cwd: root, stdio: 'inherit' });
    if (child.error) throw child.error;
    if (child.status !== 0) throw new Error(`pnpm ${args.join(' ')} failed (${child.signal ?? child.status})`);
}

/** Use the same Vite graph for selection and execution, avoiding a second compilation of every spec. */
export async function runRelatedProject({ root, configFile, sources, log = console.log }) {
    const previousEnvironment = Object.fromEntries(['TEST', 'VITEST', 'NODE_ENV'].map((key) => [key, process.env[key]]));
    process.env.TEST = 'true';
    process.env.VITEST = 'true';
    process.env.NODE_ENV ??= 'test';
    root = fs.realpathSync(root);
    let context;
    let specifications;
    try {
        try {
            const { createVitest } = await import('vitest/node');
            context = await createVitest('test', {
                root,
                config: path.resolve(root, configFile),
                watch: false,
                run: true,
                related: sources.map((file) => path.resolve(root, file)),
                coverage: { enabled: false },
                maxWorkers: 2,
                reporters: ['default', 'junit'],
                outputFile: path.resolve(root, `build/test-results/vitest/${configFile.includes('monaco') ? 'monaco-' : ''}junit.xml`),
            });
            specifications = await context.getRelevantTestSpecifications();
        } catch (error) {
            return { selectionError: error };
        }
        log(`${configFile}: ${specifications.length} affected spec(s)`);
        for (const specification of specifications) log(`  ${path.relative(root, specification.moduleId)}`);
        if (!specifications.length) return { selected: 0, success: true };
        const result = await context.start();
        return { selected: specifications.length, success: context.state.getCountOfFailedTests() === 0 && result.unhandledErrors.length === 0 && !process.exitCode };
    } finally {
        try {
            await context?.close();
        } finally {
            for (const [key, value] of Object.entries(previousEnvironment)) {
                if (value === undefined) delete process.env[key];
                else process.env[key] = value;
            }
        }
    }
}

/** GITHUB_OUTPUT makes coverage-producing full runs distinguishable from selected PR tests. */
export async function executePlan(
    plan,
    { root, outputFile = process.env.GITHUB_OUTPUT, log = console.log, runCommand: command = runCommand, runRelatedProject: project = runRelatedProject },
) {
    const report = (current) => {
        log(`Client test mode: ${current.mode}; coverage: ${current.mode === 'full'}; ${current.reason ?? ''}`);
        if (outputFile) fs.appendFileSync(outputFile, `mode=${current.mode}\ncoverage=${current.mode === 'full'}\n`);
    };
    const runFull = async (reason) => {
        report(fullPlan(reason));
        await command(['run', 'test:ci'], root);
        return 0;
    };
    if (plan.mode === 'full') return runFull(plan.reason);
    report(plan);
    // A selected run must never leave an old full-suite summary that could be mistaken for new coverage.
    fs.rmSync(path.join(root, 'build/test-results/vitest/coverage'), { recursive: true, force: true });
    if (plan.mode === 'none') return 0;
    log(`Graph seeds:\n${plan.sources.map((file) => `  ${file}`).join('\n')}`);
    await command(['run', 'prebuild'], root);
    let selected = 0;
    for (const configFile of configs) {
        const result = await project({ root, configFile, sources: plan.sources, log });
        if (result.selectionError) return runFull(`Vitest graph selection failed: ${result.selectionError.message}`);
        selected += result.selected;
        if (!result.success) return 1;
    }
    return selected ? 0 : runFull('No related specs were found for client changes');
}

async function main() {
    const { values } = parseArgs({ options: { 'base-ref': { type: 'string' }, full: { type: 'boolean', default: false } }, strict: true });
    if (!values.full && !values['base-ref']) throw new Error('Provide --base-ref <fetched ref> or --full');
    const root = fileURLToPath(new URL('../..', import.meta.url));
    let plan = fullPlan('Full client validation requested');
    if (!values.full) {
        try {
            const changes = readChanges(root, values['base-ref']);
            const files = [...new Set([...git(root, ['ls-files', '-z']).split('\0').filter(Boolean), ...changes.flatMap((change) => change.paths)])];
            plan = await planTests({ root, changes, files });
        } catch (error) {
            plan = fullPlan(`Cannot safely determine changed client inputs: ${error.message}`);
        }
    }
    process.exitCode = await executePlan(plan, { root });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch((error) => {
        console.error(error);
        process.exitCode = 1;
    });
}
