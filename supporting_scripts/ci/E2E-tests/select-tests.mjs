import { execFileSync } from 'node:child_process';
import { appendFileSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const safePath = (value) => typeof value === 'string' && /^[\w./-]+$/.test(value) && !value.split('/').includes('..') && !value.startsWith('/');
const prefixMatches = (file, prefix) => (prefix.endsWith('/') ? file.startsWith(prefix) : file === prefix);

export function discoverSpecs(root) {
    const visit = (directory) =>
        readdirSync(join(root, 'src/test/playwright', directory), { withFileTypes: true }).flatMap((entry) => {
            const path = `${directory}/${entry.name}`;
            return entry.isDirectory() ? visit(path) : entry.isFile() && path.endsWith('.spec.ts') ? [path] : [];
        });
    return visit('e2e').sort();
}

// These are conservative audit signals, not a complete JavaScript call graph. The explicit
// contract also records indirect browser/HTTP workflows (see consumerAuditNotes). Detect common
// new consumers here on every PR, rather than requiring CI-tooling changes to run an audit test.
function observedRuntimeModules(source, mapping) {
    const runtime = source.replace(/^import\b[\s\S]*?;/gm, '').replace(/\/\*[\s\S]*?\*\/|^\s*\/\/[^\n]*/gm, '');
    const modules = new Set([...runtime.matchAll(/\bapi\/(\w+)\//g)].map((match) => match[1]).filter((module) => Object.hasOwn(mapping.mappings, module)));
    for (const [module, type] of Object.entries({ quiz: 'QUIZ', programming: 'PROGRAMMING', text: 'TEXT', modeling: 'MODELING', fileupload: 'FILE_UPLOAD' })) {
        const title = module === 'fileupload' ? 'FileUpload' : module[0].toUpperCase() + module.slice(1);
        const fixture = module === 'fileupload' ? 'fileUpload' : module;
        if (new RegExp(`ExerciseType\\.${type}\\b|\\b\\w*${title}Exercise\\w*\\(|\\b${fixture}Exercise\\w*\\.`).test(runtime)) modules.add(module);
    }
    for (const [module, pattern] of Object.entries({
        lecture: /\b(?:createLecture|deleteLecture|createTextUnit)\(|\blecture(?:Management|Creation)\./,
        atlas: /\b(?:createCompetency|deleteCompetency|createCompetencyRelation|createPrerequisite|enableLearningPaths)\(|\bcompetencyManagement\./,
        communication: /\b(?:communicationAPIRequests|courseMessages|courseCommunication)\./,
        exam: /\b(?:adminExamAPIRequests|examAPIRequests|examExerciseGroupCreation|examParticipation|examTestRun|examCreation|studentExamManagement)\./,
    }))
        if (pattern.test(runtime)) modules.add(module);
    // Student course children share an active exam-state subscription in CourseOverviewComponent.
    // Match actual routes and used navigation/pageobjects; management-only /course-management
    // routes do not mount that container. See the checked-in contract's route audit notes.
    if (
        /["'`]\/courses\/|\bcourse(?:Overview|Messages|Communication)\.|\bcourseList\.openCourse\(|\bprogrammingExerciseOverview\.(?:startParticipation|checkResultScoreAfterBuild)\(|\bcompetencyManagement\.openCompetencyDetail\(/.test(
            runtime,
        )
    )
        modules.add('exam');
    return modules;
}

// The contract records browser/HTTP consumers, including setup and cleanup. A type import is
// not runtime evidence. Shared fixture bags expose all modules, so following all their imports
// would make every spec a consumer of everything. Keep the reviewed workflows explicit instead.
export function validateMapping(mapping, specs, root) {
    if (!mapping || typeof mapping.mappings !== 'object' || !mapping.mappings || Array.isArray(mapping.mappings)) throw new Error('Invalid module mappings');
    const modules = Object.keys(mapping.mappings);
    for (const [name, entry] of Object.entries(mapping.mappings)) {
        if (!Array.isArray(entry.sourcePaths) || !entry.sourcePaths.length || !entry.sourcePaths.every(safePath)) throw new Error(`Invalid source paths for ${name}`);
    }
    if (!mapping.specModules || typeof mapping.specModules !== 'object' || Array.isArray(mapping.specModules)) throw new Error('Missing spec consumer contract');
    for (const [spec, consumers] of Object.entries(mapping.specModules)) {
        if (!safePath(spec) || !spec.endsWith('.spec.ts') || !Array.isArray(consumers) || !consumers.length || !consumers.every((module) => modules.includes(module))) {
            throw new Error(`Invalid consumer contract for ${spec}`);
        }
    }
    // An exact client override is opt-in and must name the broader domain it replaces.
    // Never let a directory prefix or an empty/stale consumer list silently narrow a module.
    for (const [name, entry] of Object.entries(mapping.mappings)) {
        if (entry.replacesModule === undefined) continue;
        const consumers = Object.entries(mapping.specModules)
            .filter(([, consumed]) => consumed.includes(name))
            .map(([spec]) => spec);
        if (
            !modules.includes(entry.replacesModule) ||
            entry.replacesModule === name ||
            mapping.mappings[entry.replacesModule].replacesModule !== undefined ||
            typeof entry.audit !== 'string' ||
            !entry.audit.trim() ||
            !entry.sourcePaths.every((path) => /^src\/main\/webapp\/app\/.+\.(?:ts|html|scss|css)$/.test(path) && !path.endsWith('.spec.ts')) ||
            !consumers.length ||
            !consumers.every((spec) => specs.includes(spec))
        )
            throw new Error(`Invalid or incomplete exact client scope '${name}'`);
    }
    for (const spec of specs) {
        if (!safePath(spec) || !Object.hasOwn(mapping.specModules, spec)) throw new Error(`Unassigned spec '${spec}'; audit its runtime consumers`);
        if (root) {
            for (const module of observedRuntimeModules(readFileSync(join(root, 'src/test/playwright', spec), 'utf8'), mapping)) {
                if (!mapping.specModules[spec].includes(module)) throw new Error(`Spec '${spec}' has an unaudited '${module}' runtime consumer`);
            }
        }
    }
    if (!Array.isArray(mapping.alwaysRunTests) || !mapping.alwaysRunTests.length || !mapping.alwaysRunTests.every((spec) => specs.includes(spec)))
        throw new Error('Missing always-run smoke spec');
    for (const field of ['runAllTestsPatterns', 'ignoredPaths']) {
        if (!Array.isArray(mapping[field]) || !mapping[field].every(safePath)) throw new Error(`Invalid ${field}`);
    }
    for (const entry of mapping.catalogModules ?? []) {
        if (!Array.isArray(entry.files) || !entry.files.length || !entry.files.every(safePath) || !modules.includes(entry.module))
            throw new Error('Invalid translation catalog mapping');
    }
}

export function selectTests(root, changedFiles, mapping) {
    const full = (reason) => ({ runAll: true, relevant: [], remaining: [], reason });
    let specs;
    try {
        specs = discoverSpecs(root);
        if (!specs.length) return full('No E2E specs found.');
        validateMapping(mapping, specs, root);
    } catch (error) {
        return full(`Mapping audit failed: ${error.message}`);
    }
    if (!changedFiles.length) return full('No changed files detected.');
    const selected = new Set(mapping.alwaysRunTests);
    const affected = new Set();
    for (const file of changedFiles) {
        if (!safePath(file)) return full(`Unsupported changed path '${JSON.stringify(file)}'.`);
        if (mapping.runAllTestsPatterns.some((prefix) => prefixMatches(file, prefix)) || /^src\/main\/java\/de\/tum\/cit\/aet\/artemis\/[^/]+\/(?:config|security)\//.test(file))
            return full(`Global or runtime input '${file}'.`);
        if (file.startsWith('src/test/playwright/')) {
            const spec = file.slice('src/test/playwright/'.length);
            if (!spec.startsWith('e2e/') || !spec.endsWith('.spec.ts')) return full(`Playwright infrastructure '${file}'.`);
            if (specs.includes(spec)) selected.add(spec); // Deleted specs must never become runner arguments.
            continue;
        }
        if (mapping.ignoredPaths.some((prefix) => prefixMatches(file, prefix)) || /^src\/test\/java\/.*\.java$/.test(file) || /^src\/main\/webapp\/app\/.*\.spec\.ts$/.test(file))
            continue;
        const matches = Object.entries(mapping.mappings).filter(([, entry]) => entry.sourcePaths.some((prefix) => prefixMatches(file, prefix)));
        // Apply overrides per changed file, so another broad input still contributes all its
        // consumers. Only audited exact client files can replace their domain's broad mapping.
        const replaced = new Set(matches.map(([, entry]) => entry.replacesModule).filter(Boolean));
        let modules = matches.filter(([module]) => !replaced.has(module)).map(([module]) => module);
        const catalog = file.match(/^src\/main\/webapp\/i18n\/[^/]+\/([^/]+\.json)$/);
        if (catalog) modules = (mapping.catalogModules ?? []).filter((entry) => entry.files.includes(catalog[1])).map((entry) => entry.module);
        if (!modules.length || !/\.(?:java|ts|html|scss|css|json|svg)$/.test(file)) return full(`No selective mapping for '${file}'.`);
        for (const module of modules) {
            const consumers = specs.filter((spec) => mapping.specModules[spec].includes(module));
            if (!consumers.length) return full(`Module '${module}' has no audited runtime consumers.`);
            affected.add(module);
            consumers.forEach((spec) => selected.add(spec));
        }
    }
    const relevant = specs.filter((spec) => selected.has(spec));
    const remaining = specs.filter((spec) => !selected.has(spec));
    return {
        runAll: false,
        relevant,
        remaining,
        reason: `Affected modules: ${[...affected].sort().join(', ') || 'changed specs / non-runtime files'}; includes authentication and health smoke.`,
    };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
    const root = join(scriptDirectory, '../../..');
    let result;
    try {
        const changes = execFileSync('git', ['diff', '--no-renames', '--name-only', '-z', `${process.argv[2] ?? 'origin/develop'}...HEAD`], {
            cwd: root,
            encoding: 'utf8',
            maxBuffer: 16 * 1024 * 1024,
        });
        result = selectTests(root, changes.split('\0').filter(Boolean), JSON.parse(readFileSync(join(scriptDirectory, 'e2e-test-mapping.json'), 'utf8')));
    } catch (error) {
        result = { runAll: true, relevant: [], remaining: [], reason: `Unable to establish a safe selection: ${error.message}` };
    }
    console.log(result.reason);
    const outputs = {
        RUN_ALL_TESTS: String(result.runAll),
        RELEVANT_TESTS: result.relevant.join(' '),
        REMAINING_TESTS: result.remaining.join(' '),
        RELEVANT_COUNT: result.relevant.length,
        REMAINING_COUNT: result.remaining.length,
    };
    if (process.env.GITHUB_STEP_SUMMARY) {
        appendFileSync(
            process.env.GITHUB_STEP_SUMMARY,
            `### E2E selection\n\n${result.runAll ? 'Full suite required' : `Selected ${result.relevant.length} specs; ${result.remaining.length} specs in the full-mode complement`}.\n\n${result.reason}\n\nThe workflow decides whether the complement runs.\n`,
        );
    }
    for (const [key, value] of Object.entries(outputs)) {
        console.log(`OUTPUT: ${key}=${value}`);
        if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, `${key}=${value}\n`);
    }
}
