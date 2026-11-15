import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';

import { SecurityPolicyDocument, SecurityPolicyFile } from 'app/programming/shared/entities/security-policy.model';
import {
    RowSource,
    classesOfTests,
    declaredTestClasses,
    diffLines,
    diffStats,
    editorPolicyOf,
    formatAresDate,
    nameFromPath,
    parseAresDate,
    policyFileName,
    policyPathFor,
    rebasePolicy,
    sourcedRows,
    summarizeChanges,
    testClassOf,
    withAssignment,
    withRows,
} from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';

const DOCUMENT: SecurityPolicyDocument = {
    policyPath: 'SecurityPolicy.yaml',
    languageConfiguration: 'JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ',
    supervisedPackage: 'de.example',
    testClasses: ['de.example.SortingTest', 'de.example.Helper'],
    files: [
        { path: 'data/input.txt', read: true, overwrite: false, create: false, execute: false, delete: false },
        { path: 'logs/', read: false, overwrite: true, create: true, execute: false, delete: false },
    ],
    threads: [{ threadClass: 'java.lang.Thread', maxThreads: 1 }],
};

const FILE: SecurityPolicyFile = {
    policyPath: 'SecurityPolicy.yaml',
    content: 'thisPolicyFileCompliesToThePolicyVersion: 1\n',
    document: DOCUMENT,
    assignedTests: ['de.example.SortingTest'],
};

describe('security-policy-editor.util', () => {
    it('tags each row as preset, committed, new, changed or removed', () => {
        const working = withRows(DOCUMENT, 'files', [
            { path: 'data/input.txt', read: true, overwrite: true, create: false, execute: false, delete: false },
            { path: 'tmp/', read: true, overwrite: false, create: false, execute: false, delete: false },
        ]);
        const preset = { name: 'MOCKITO', label: 'Mockito', files: [{ path: 'mock/', read: true, overwrite: false, create: false, execute: false, delete: false }] };

        const rows = sourcedRows('files', DOCUMENT, working, preset);

        expect(rows.map((row) => [row.key, row.source])).toEqual([
            ['mock/', RowSource.PRESET],
            ['data/input.txt', RowSource.CHANGED],
            ['tmp/', RowSource.NEW],
            ['logs/', RowSource.REMOVED],
        ]);
        expect(rows[0].preset).toBe('Mockito');
        expect(sourcedRows('threads', DOCUMENT, DOCUMENT, undefined).map((row) => row.source)).toEqual([RowSource.COMMITTED]);
    });

    it('reads a committed policy with its tests and the classes no test needs', () => {
        const policy = editorPolicyOf(FILE);

        expect(policy.name).toBe('Security Policy');
        expect(policy.tests).toEqual(['de.example.SortingTest']);
        expect(policy.extraClasses).toEqual(['de.example.Helper']);
        expect(summarizeChanges(policy).total).toBe(0);
    });

    it('declares the classes of the assigned tests and the extra classes, keeping the committed order', () => {
        expect(declaredTestClasses(['de.b.B', 'de.a.A'], ['de.a.A#test', 'de.c.C'], ['de.b.B'])).toEqual(['de.b.B', 'de.a.A', 'de.c.C']);
        expect(classesOfTests(['de.a.A#one', 'de.a.A#two', 'de.b.B'])).toEqual(['de.a.A', 'de.b.B']);
        expect(testClassOf('de.a.A#one')).toBe('de.a.A');
        expect(testClassOf('de.a.A')).toBe('de.a.A');
    });

    it('counts every kind of unsaved change', () => {
        const policy = editorPolicyOf(FILE);
        const assigned = withAssignment(policy, ['de.example.SortingTest#testBubbleSort', 'de.example.StressTest'], []);
        const changed = {
            ...assigned,
            name: 'Stress Tests',
            working: withRows(assigned.working!, 'packages', [{ packageName: 'org.apache.commons' }]),
        };

        const summary = summarizeChanges(changed);

        expect(summary.restrictions).toBe(1);
        expect(summary.restrictionsByKind.packages).toBe(1);
        expect(summary.testCases).toBe(3);
        expect(summary.testClasses).toBe(1);
        expect(summary.settings).toBe(1);
        expect(summary.isNew).toBe(false);
        expect(summary.total).toBe(6);
        expect(changed.working.testClasses).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
    });

    it('keeps the unsaved changes when rebasing onto a newer commit', () => {
        const policy = withAssignment(editorPolicyOf(FILE), ['de.example.SortingTest', 'de.example.StressTest'], ['de.example.Helper']);
        const edited = { ...policy, working: withRows(policy.working!, 'packages', [{ packageName: 'org.apache.commons' }]) };
        const newer: SecurityPolicyFile = {
            ...FILE,
            document: { ...DOCUMENT, languageConfiguration: 'JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION', threads: [] },
        };

        const rebased = rebasePolicy(edited, newer);

        expect(rebased.working?.languageConfiguration).toBe('JAVA_USING_GRADLE_WALA_AND_INSTRUMENTATION');
        expect(rebased.working?.threads).toEqual([]);
        expect(rebased.working?.packages).toEqual([{ packageName: 'org.apache.commons' }]);
        expect(rebased.tests).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
        expect(rebased.baseTests).toEqual(['de.example.SortingTest']);
    });

    it('derives file names and paths from names', () => {
        expect(policyFileName('Stress tests')).toBe('StressTests.yaml');
        expect(policyFileName('  ')).toBe('');
        expect(nameFromPath('policies/StressTests.yaml')).toBe('Stress Tests');
        expect(policyPathFor('Security Policy', 'policies/SecurityPolicy.yaml', [])).toBe('policies/SecurityPolicy.yaml');
        expect(policyPathFor('Stress', 'policies/SecurityPolicy.yaml', [])).toBe('policies/Stress.yaml');
        expect(policyPathFor('Stress', undefined, ['test/SecurityPolicy.yaml'])).toBe('test/Stress.yaml');
    });

    it('writes and reads the dates of hidden tests', () => {
        const date = dayjs('2026-12-24 23:59', 'YYYY-MM-DD HH:mm');

        expect(formatAresDate(date)).toMatch(/^2026-12-24 23:59 \S+$/);
        expect(parseAresDate(formatAresDate(date))?.isSame(date)).toBe(true);
        expect(parseAresDate('soon')).toBeUndefined();
    });

    it('diffs two files side by side and collapses unchanged lines', () => {
        const before = 'a\nb\nc\nd\ne\nf\n';
        const after = 'a\nb\nc\nd\nE\nf\ng\n';

        const rows = diffLines(before, after);

        expect(rows[0]).toEqual({ kind: 'collapsed', count: 3 });
        expect(rows.find((row) => row.kind === 'changed')).toEqual({ kind: 'changed', left: { number: 5, text: 'e' }, right: { number: 5, text: 'E' } });
        expect(diffStats(rows)).toEqual({ added: 2, removed: 1 });
    });
});
