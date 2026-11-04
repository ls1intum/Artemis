import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { discoverSpecs, selectTests, validateMapping } from './select-tests.mjs';

const directory = dirname(fileURLToPath(import.meta.url));
const root = join(directory, '../../..');

try {
    const mapping = JSON.parse(readFileSync(join(directory, 'e2e-test-mapping.json'), 'utf8'));
    const specs = discoverSpecs(root);
    validateMapping(mapping, specs, root);
    assert.ok(specs.length, 'No E2E specs found');
    const args = process.argv.slice(2);
    const modules = Object.keys(mapping.mappings).sort();
    if (!args.length) throw new Error(`Usage: verify-test-determination.sh <module...> | --all\nAvailable modules: ${modules.join(', ')}`);
    const all = args.length === 1 && args[0] === '--all';
    if (!all) {
        for (const module of args) if (!Object.hasOwn(mapping.mappings, module)) throw new Error(`Unknown module '${module}'. Available modules: ${modules.join(', ')}`);
    }
    // --all checks each module separately, including exact-file client scopes and global fallbacks.
    // Explicit module arguments model their combined diff through the production selector.
    const cases = all ? modules.map((module) => [module]) : [[...new Set(args)]];
    for (const selectedModules of cases) {
        const files = selectedModules.flatMap((module) =>
            mapping.mappings[module].sourcePaths.map((path) => (path.endsWith('/') ? `${path}SelectionVerification.${path.startsWith('src/main/java/') ? 'java' : 'ts'}` : path)),
        );
        const result = selectTests(root, files, mapping);
        // Full fallback runs one full-suite job rather than the two selective phases.
        const selected = result.runAll ? specs : result.relevant;
        const remaining = result.runAll ? [] : result.remaining;
        assert.equal(new Set(selected).size, selected.length, 'Duplicate spec in selected phase');
        assert.equal(new Set(remaining).size, remaining.length, 'Duplicate spec in remaining phase');
        assert.equal(new Set([...selected, ...remaining]).size, selected.length + remaining.length, 'A spec would execute in both phases');
        assert.deepEqual([...selected, ...remaining].sort(), specs, 'Selection must cover every current spec exactly once');
        console.log(`Modules: ${selectedModules.join(', ')}`);
        console.log(`Reason: ${result.reason}`);
        console.log(`Selected specs: ${selected.length}`);
        console.log(`Remaining specs: ${remaining.length}`);
        console.log(`Total specs: ${specs.length}`);
        console.log(`RESULT: PASS - ${result.runAll ? 'Full suite in one execution' : 'Disjoint exact partition of selected and remaining specs'}`);
    }
} catch (error) {
    console.error(`RESULT: FAIL - ${error.message}`);
    process.exitCode = 1;
}
