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
// Shared components, services and utilities have the same import edges as feature code. Keep
// generated clients, bootstrap and runtime configuration global; do not use module names as
// a substitute for the dependency graph.
const globalSource = /^(?:openapi\/|core\/(?:config|environments|util|global)\/|[^/]+$)/;
// Match ci.yml's client_tests exclusions. Every other path is relevant by default: prebuild and
// custom rule tests also consume global inputs such as .nvmrc, build.gradle and gradle/.
const nonClientPrefixes = ['documentation/', 'src/main/java/', 'src/test/java/', 'src/main/resources/', 'src/test/resources/'];
const configs = ['vitest.config.ts', 'config/testing/vitest.monaco.config.ts'];
const fullPlan = (reason) => ({ mode: 'full', reason });

function clientRelevant(file) {
    return !file.endsWith('.md') && file !== 'LICENSE' && !/^src\/test\/playwright\/e2e\/.*\.spec\.ts$/.test(file) && !nonClientPrefixes.some((prefix) => file.startsWith(prefix));
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
        if (infrastructure.test(file) || (file.startsWith(appRoot) && globalSource.test(file.slice(appRoot.length)))) {
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

async function withTestEnvironment(operation) {
    const previousEnvironment = Object.fromEntries(['TEST', 'VITEST', 'NODE_ENV'].map((key) => [key, process.env[key]]));
    process.env.TEST = 'true';
    process.env.VITEST = 'true';
    process.env.NODE_ENV ??= 'test';
    try {
        return await operation();
    } finally {
        for (const [key, value] of Object.entries(previousEnvironment)) {
            if (value === undefined) delete process.env[key];
            else process.env[key] = value;
        }
    }
}

/** Keep the discovered Vite graph open until both projects prove every changed input has tests. */
export async function prepareRelatedProject({ root, configFile, sources, log = console.log }) {
    let context;
    let closed = false;
    const close = async () => {
        if (closed) return;
        closed = true;
        await withTestEnvironment(() => context?.close());
    };
    try {
        return await withTestEnvironment(async () => {
            root = fs.realpathSync(root);
            let specifications;
            const relatedSources = [];
            let relatedSelected;
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
            const selected = new Map();
            // Resolve each input independently: a tested component must not hide an untested
            // service in the same PR. Vite reuses transformed modules across these graph walks.
            for (const source of sources) {
                context.config.related = [path.resolve(root, source)];
                const related = await context.getRelevantTestSpecifications();
                if (related.length) relatedSources.push(source);
                for (const specification of related) selected.set(specification.taskId, specification);
            }
            specifications = [...selected.values()];
            relatedSelected = specifications.length;
            // Filesystem reads do not create Vite import edges to the source being checked.
            // Include these guardrails on every affected run, even when ordinary related specs exist.
            const ts = await import('typescript');
            const guards = (await context.globTestSpecifications()).filter((specification) =>
                ts.preProcessFile(fs.readFileSync(specification.moduleId, 'utf8'), true, true).importedFiles.some(({ fileName }) => /^(?:node:)?fs(?:\/promises)?$/.test(fileName)),
            );
            // Guard specs are already discovered. Adding them directly avoids a second complete
            // graph walk and keeps their presence separate from proof of a related test per input.
            for (const specification of guards) selected.set(specification.taskId, specification);
            specifications = [...selected.values()];

            log(`${configFile}: ${specifications.length} affected spec(s)`);
            for (const specification of specifications) log(`  ${path.relative(root, specification.moduleId)}`);
            const selection = { selected: specifications.length, relatedSelected, relatedSources };
            return {
                ...selection,
                close,
                run: async () =>
                    withTestEnvironment(async () => {
                        if (closed) throw new Error(`Cannot run closed Vitest project: ${configFile}`);
                        if (!specifications.length) return { ...selection, success: true };
                        // The default reporter's related summary describes only the final per-input
                        // query. Our explicit list describes the union including guardrails.
                        context.config.related = undefined;
                        await context.standalone();
                        // start() rediscovers the same graph; execute the exact prepared specs.
                        const result = await context.runTestSpecifications(specifications);
                        return {
                            ...selection,
                            success: context.state.getCountOfFailedTests() === 0 && result.unhandledErrors.length === 0 && !process.exitCode,
                        };
                    }),
            };
        });
    } catch (error) {
        return { selectionError: error, close };
    }
}

/** Standalone execution and selection-only inspection share the same preparation lifecycle. */
export async function runRelatedProject({ execute = true, ...options }) {
    const project = await prepareRelatedProject(options);
    try {
        if (project.selectionError) return { selectionError: project.selectionError };
        if (execute) return await project.run();
        return { selected: project.selected, relatedSelected: project.relatedSelected, relatedSources: project.relatedSources, success: true };
    } finally {
        await project.close();
    }
}

/** GITHUB_OUTPUT makes coverage-producing full runs distinguishable from selected PR tests. */
export async function executePlan(
    plan,
    { root, outputFile = process.env.GITHUB_OUTPUT, log = console.log, runCommand: command = runCommand, prepareRelatedProject: prepare = prepareRelatedProject, prebuilt = false },
) {
    const report = (current) => {
        log(`Client test mode: ${current.mode}; coverage: ${current.mode === 'full'}; ${current.reason ?? ''}`);
        if (outputFile) fs.appendFileSync(outputFile, `mode=${current.mode}\ncoverage=${current.mode === 'full'}\n`);
    };
    const runFull = async (reason) => {
        report(fullPlan(reason));
        if (prebuilt) {
            await command(['run', 'vitest:ci'], root);
            await command(['run', 'vitest:monaco'], root);
        } else await command(['run', 'test:ci'], root);
        return 0;
    };
    if (plan.mode === 'full') return runFull(plan.reason);
    report(plan);
    // A selected run must never leave an old full-suite summary that could be mistaken for new coverage.
    fs.rmSync(path.join(root, 'build/test-results/vitest/coverage'), { recursive: true, force: true });
    if (plan.mode === 'none') return 0;
    log(`Graph seeds:\n${plan.sources.map((file) => `  ${file}`).join('\n')}`);
    if (!prebuilt) await command(['run', 'prebuild'], root);
    prebuilt = true;
    const preparedProjects = [];
    const relatedSources = new Set();
    let fallbackReason;
    let selectionError;
    let status = 0;
    const executionErrors = [];
    const closeErrors = [];
    try {
        for (const configFile of configs) {
            const project = await prepare({ root, configFile, sources: plan.sources, log });
            preparedProjects.push(project);
            if (project.selectionError) {
                selectionError = project.selectionError;
                fallbackReason = `Vitest graph selection failed: ${project.selectionError.message}`;
                break;
            }
            for (const source of project.relatedSources ?? []) relatedSources.add(source);
        }
        const untested = plan.sources.filter((source) => !relatedSources.has(source));
        if (!fallbackReason && untested.length) fallbackReason = `No related specs were found for client changes: ${untested.join(', ')}`;
        if (!fallbackReason) {
            for (const project of preparedProjects) {
                const result = await project.run();
                if (!result.success) {
                    status = 1;
                    break;
                }
            }
        }
    } catch (error) {
        executionErrors.push(error);
    } finally {
        // Release every graph before a full fallback starts. Each operation restores its own
        // environment, so opening two contexts cannot leave TEST/VITEST set for prebuild.
        for (const project of preparedProjects.reverse()) {
            try {
                await project.close();
            } catch (error) {
                closeErrors.push(error);
            }
        }
    }
    if (closeErrors.length) throw new AggregateError([...executionErrors, ...(selectionError ? [selectionError] : []), ...closeErrors], 'Cannot close prepared Vitest projects');
    if (executionErrors.length) throw executionErrors[0];
    return fallbackReason ? runFull(fallbackReason) : status;
}

async function main() {
    const { values } = parseArgs({
        options: { 'base-ref': { type: 'string' }, full: { type: 'boolean', default: false }, prebuilt: { type: 'boolean', default: false } },
        strict: true,
    });
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
    process.exitCode = await executePlan(plan, { root, prebuilt: values.prebuilt });
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch((error) => {
        console.error(error);
        process.exitCode = 1;
    });
}
