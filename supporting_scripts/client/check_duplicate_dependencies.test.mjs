import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { evaluate, findBundledDuplicates, findDuplicates, parseLockfile, splitSnapshotKey } from './check_duplicate_dependencies.mjs';

function lockfile({ importers, snapshots }) {
    return { importers, snapshots };
}

const importer = (dependencies, devDependencies = {}, optionalDependencies = {}) => ({
    dependencies: Object.fromEntries(Object.entries(dependencies).map(([name, version]) => [name, { specifier: version, version }])),
    devDependencies: Object.fromEntries(Object.entries(devDependencies).map(([name, version]) => [name, { specifier: version, version }])),
    optionalDependencies: Object.fromEntries(Object.entries(optionalDependencies).map(([name, version]) => [name, { specifier: version, version }])),
});

describe('splitSnapshotKey', () => {
    it('splits plain and scoped keys and drops the peer suffix', () => {
        assert.deepEqual(splitSnapshotKey('left-pad@1.3.0'), { name: 'left-pad', version: '1.3.0' });
        assert.deepEqual(splitSnapshotKey('@scope/pkg@2.0.1(react@19.0.0)(@types/react@19.0.0)'), { name: '@scope/pkg', version: '2.0.1' });
    });
});

describe('findDuplicates', () => {
    it('reports a package that two dependencies resolve to different versions', () => {
        const duplicates = findDuplicates(
            lockfile({
                importers: { '.': importer({ a: '1.0.0', b: '1.0.0' }) },
                snapshots: {
                    'a@1.0.0': { dependencies: { zustand: '5.1.0(react@19.0.0)' } },
                    'b@1.0.0': { dependencies: { zustand: '4.5.7' } },
                    'zustand@5.1.0(react@19.0.0)': {},
                    'zustand@4.5.7': {},
                },
            }),
        );
        assert.deepEqual([...duplicates], [['zustand', ['4.5.7', '5.1.0']]]);
    });

    it('counts one version with different peer suffixes once', () => {
        const duplicates = findDuplicates(
            lockfile({
                importers: { '.': importer({ a: '1.0.0', b: '1.0.0' }) },
                snapshots: {
                    'a@1.0.0': { dependencies: { shared: '1.0.0(x@1)' } },
                    'b@1.0.0': { dependencies: { shared: '1.0.0(x@2)' } },
                    'shared@1.0.0(x@1)': {},
                    'shared@1.0.0(x@2)': {},
                },
            }),
        );
        assert.equal(duplicates.size, 0);
    });

    it('ignores packages that only development dependencies reach', () => {
        const duplicates = findDuplicates(
            lockfile({
                importers: { '.': importer({ a: '1.0.0' }, { tool: '1.0.0' }) },
                snapshots: {
                    'a@1.0.0': { dependencies: { shared: '1.0.0' } },
                    'tool@1.0.0': { dependencies: { shared: '2.0.0' } },
                    'shared@1.0.0': {},
                    'shared@2.0.0': {},
                },
            }),
        );
        assert.equal(duplicates.size, 0);
    });

    it('seeds the closure with the optional dependencies of a workspace project', () => {
        const duplicates = findDuplicates(
            lockfile({
                importers: { '.': importer({ a: '1.0.0' }, {}, { shared: '2.0.0' }) },
                snapshots: {
                    'a@1.0.0': { dependencies: { shared: '1.0.0' } },
                    'shared@1.0.0': {},
                    'shared@2.0.0': {},
                },
            }),
        );
        assert.deepEqual([...duplicates], [['shared', ['1.0.0', '2.0.0']]]);
    });

    it('follows optional dependencies and the dependencies of every workspace project', () => {
        const duplicates = findDuplicates(
            lockfile({
                importers: { '.': importer({ a: '1.0.0' }), 'packages/kit': importer({ b: '1.0.0', sibling: 'link:../sibling' }) },
                snapshots: {
                    'a@1.0.0': { optionalDependencies: { shared: '1.0.0' } },
                    'b@1.0.0': { dependencies: { shared: '2.0.0' } },
                    'shared@1.0.0': {},
                    'shared@2.0.0': {},
                },
            }),
        );
        assert.deepEqual([...duplicates], [['shared', ['1.0.0', '2.0.0']]]);
    });

    it('resolves npm aliases to the package they point to', () => {
        const duplicates = findDuplicates(
            lockfile({
                importers: { '.': importer({ a: '1.0.0', alias: 'npm:real@2.0.0' }) },
                snapshots: { 'a@1.0.0': { dependencies: { real: '1.0.0' } }, 'real@1.0.0': {}, 'real@2.0.0': {} },
            }),
        );
        assert.deepEqual([...duplicates], [['real', ['1.0.0', '2.0.0']]]);
    });
});

describe('parseLockfile', () => {
    it('uses the last of the two YAML documents of a pnpm lockfile', () => {
        const parsed = parseLockfile("lockfileVersion: '9.0'\nimporters: {}\n---\nlockfileVersion: '9.0'\nimporters:\n  .: {}\nsnapshots: {}\n");
        assert.deepEqual(Object.keys(parsed.importers), ['.']);
    });
});

describe('findBundledDuplicates', () => {
    const path = (name, version) => `node_modules/.pnpm/${name.replace('/', '+')}@${version}_react@19.0.0/node_modules/${name}/dist/index.js`;

    it('reports a package bundled in two versions and ignores non-JavaScript outputs', () => {
        const duplicates = findBundledDuplicates({
            outputs: {
                'chunk-a.js': { inputs: { [path('zustand', '4.5.7')]: { bytesInOutput: 10 }, [path('@xyflow/react', '12.12.0')]: { bytesInOutput: 10 } } },
                'chunk-b.js': { inputs: { [path('zustand', '5.1.0')]: { bytesInOutput: 10 } } },
                'styles.css': { inputs: { [path('other', '1.0.0')]: { bytesInOutput: 10 }, [path('other', '2.0.0')]: { bytesInOutput: 10 } } },
            },
        });
        assert.deepEqual([...duplicates], [['zustand', ['4.5.7', '5.1.0']]]);
    });

    it('ignores an input that contributes no bytes, but reports two versions that both do', () => {
        const withZeroBytes = findBundledDuplicates({
            outputs: { 'chunk.js': { inputs: { [path('shared', '1.0.0')]: { bytesInOutput: 10 }, [path('shared', '2.0.0')]: { bytesInOutput: 0 } } } },
        });
        assert.equal(withZeroBytes.size, 0);

        const withBytes = findBundledDuplicates({
            outputs: { 'chunk.js': { inputs: { [path('shared', '1.0.0')]: { bytesInOutput: 10 }, [path('shared', '2.0.0')]: { bytesInOutput: 1 } } } },
        });
        assert.deepEqual([...withBytes], [['shared', ['1.0.0', '2.0.0']]]);
    });
});

describe('evaluate', () => {
    const duplicates = new Map([['entities', ['8.1.0', '9.0.0']]]);

    it('passes an allowlisted duplicate with the same versions', () => {
        assert.deepEqual(evaluate(duplicates, { entities: { versions: ['9.0.0', '8.1.0'], reason: 'x' } }), []);
    });

    it('fails a duplicate that is not allowlisted and names the package', () => {
        const problems = evaluate(duplicates, {});
        assert.equal(problems.length, 1);
        assert.match(problems[0], /entities resolves to 8\.1\.0 and 9\.0\.0/);
    });

    it('fails an allowlisted duplicate whose versions changed', () => {
        const problems = evaluate(duplicates, { entities: { versions: ['7.0.0', '9.0.0'], reason: 'x' } });
        assert.match(problems[0], /now resolves to 8\.1\.0 and 9\.0\.0/);
    });

    it('fails an allowlist entry that is no longer duplicated', () => {
        assert.match(evaluate(new Map(), { entities: { versions: ['8.1.0', '9.0.0'], reason: 'x' } })[0], /no longer duplicated/);
    });

    describe('for a bundle', () => {
        const allowlist = { entities: { versions: ['8.1.0', '9.0.0'], reason: 'x' } };

        it('passes when the bundled versions are the allowed ones', () => {
            assert.deepEqual(evaluate(duplicates, allowlist, { bundle: true }), []);
        });

        it('passes when the bundle contains only a subset of the allowed versions or none of the allowlisted packages', () => {
            assert.deepEqual(evaluate(new Map([['entities', ['9.0.0']]]), allowlist, { bundle: true }), []);
            assert.deepEqual(evaluate(new Map(), allowlist, { bundle: true }), []);
        });

        it('fails an allowlisted package with a bundled version outside the allowed ones', () => {
            const problems = evaluate(new Map([['entities', ['7.0.0', '9.0.0']]]), allowlist, { bundle: true });
            assert.equal(problems.length, 1);
            assert.match(problems[0], /7\.0\.0 is bundled as well/);
        });

        it('fails a package that is not allowlisted', () => {
            assert.match(evaluate(new Map([['zustand', ['4.5.7', '5.1.0']]]), allowlist, { bundle: true })[0], /zustand resolves to 4\.5\.7 and 5\.1\.0/);
        });
    });
});
