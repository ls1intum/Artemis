import { describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';

import { SecurityPolicyDocument, SecurityPolicyFile } from 'app/programming/shared/entities/security-policy.model';
import {
    RowSource,
    changedLineNumbers,
    classesOfTests,
    countKey,
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
    withRestoredEntry,
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
    it('names a count of exactly one in the singular and every other count in the plural', () => {
        expect([0, 1, 2].map((count) => countKey('changes', count))).toEqual(['changes.other', 'changes.one', 'changes.other']);
    });

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

    it('shows a row of the document that the preset also adds as a row of the document, so it can still be edited or removed', () => {
        const mock = { path: 'mock/', read: true, overwrite: false, create: false, execute: false, delete: false };
        const preset = { name: 'MOCKITO', label: 'Mockito', files: [mock] };
        const working = withRows(DOCUMENT, 'files', [...(DOCUMENT.files ?? []), mock]);

        expect(sourcedRows('files', DOCUMENT, working, preset).map((row) => [row.key, row.source])).toEqual([
            ['data/input.txt', RowSource.COMMITTED],
            ['logs/', RowSource.COMMITTED],
            ['mock/', RowSource.NEW],
        ]);
        expect(sourcedRows('files', DOCUMENT, DOCUMENT, preset).map((row) => [row.key, row.source])).toEqual([
            ['mock/', RowSource.PRESET],
            ['data/input.txt', RowSource.COMMITTED],
            ['logs/', RowSource.COMMITTED],
        ]);
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
        // A declaration of single test cases stays while its class is declared.
        expect(declaredTestClasses(['de.a.A#one'], ['de.a.A#one', 'de.b.B'], [])).toEqual(['de.a.A#one', 'de.a.A', 'de.b.B']);
        expect(declaredTestClasses(['de.a.A#one'], ['de.b.B'], [])).toEqual(['de.b.B']);
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

    it('counts a declared test class that only an import changed, and a declaration that changes with its tests once', () => {
        const file: SecurityPolicyFile = {
            ...FILE,
            document: { ...DOCUMENT, testClasses: ['de.example.SortingTest#first', 'de.example.Helper'] },
            assignedTests: ['de.example.SortingTest#first'],
        };
        const policy = editorPolicyOf(file);
        const imported = { ...policy, working: { ...policy.working!, testClasses: ['de.example.SortingTest#second', 'de.example.Helper'] } };

        expect(summarizeChanges(policy).total).toBe(0);
        expect(summarizeChanges(imported).testClasses).toBe(2);
        expect(summarizeChanges(imported).total).toBe(2);

        // A reload rebases the unsaved changes onto the committed file, also an unchanged one, and keeps the imported declaration.
        const rebased = rebasePolicy(imported, file).working?.testClasses;
        expect(rebased).toContain('de.example.SortingTest#second');
        expect(rebased).not.toContain('de.example.SortingTest#first');

        const reassigned = withAssignment(policy, ['de.example.StressTest'], ['de.example.Helper']);
        expect(reassigned.working?.testClasses).toEqual(['de.example.Helper', 'de.example.StressTest']);
        expect(summarizeChanges(reassigned).testCases).toBe(2);
        expect(summarizeChanges(reassigned).testClasses).toBe(0);
    });

    it('counts and keeps the settings of a file that an import changes', () => {
        const policy = editorPolicyOf(FILE);
        const imported = { ...policy, working: { ...policy.working!, supervisedPackage: 'de.other', timeouts: [1000] } };

        expect(summarizeChanges(imported).settings).toBe(2);
        expect(summarizeChanges(imported).total).toBe(2);

        const rebased = rebasePolicy(imported, { ...FILE, document: { ...DOCUMENT, mainClass: 'de.example.Main' } });
        expect(rebased.working?.supervisedPackage).toBe('de.other');
        expect(rebased.working?.timeouts).toEqual([1000]);
        expect(rebased.working?.mainClass).toBe('de.example.Main');
    });

    it('lets a whole class assigned by the newer commit replace the test cases assigned in the unsaved changes', () => {
        const policy = withAssignment(editorPolicyOf(FILE), ['de.example.SortingTest', 'de.example.StressTest#second'], []);
        const newer: SecurityPolicyFile = { ...FILE, assignedTests: ['de.example.SortingTest', 'de.example.StressTest'] };

        expect(rebasePolicy(policy, newer).tests).toEqual(['de.example.SortingTest', 'de.example.StressTest']);
        expect(changedLineNumbers('a\nb\n', 'a\nb\n')).toEqual(new Set());
    });

    it('restores an entry without assigning a whole class together with one of its test cases', () => {
        expect(withRestoredEntry(['de.a.A#one', 'de.a.A#two', 'de.b.B'], 'de.a.A')).toEqual(['de.b.B', 'de.a.A']);
        expect(withRestoredEntry(['de.a.A'], 'de.a.A#one')).toEqual(['de.a.A']);
        expect(withRestoredEntry(['de.a.A#two'], 'de.a.A#one')).toEqual(['de.a.A#two', 'de.a.A#one']);
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
        // A date written in another time zone keeps its instant.
        expect(parseAresDate('2026-12-24 10:00 America/New_York')?.toISOString()).toBe('2026-12-24T15:00:00.000Z');
        expect(parseAresDate('2026-12-24 10:00 Not/AZone')).toBeUndefined();
    });

    it('resolves a repeated or skipped wall time like java.time, whatever the date the editor runs on', () => {
        vi.useFakeTimers();
        try {
            for (const today of ['2026-01-15T12:00:00Z', '2026-07-15T12:00:00Z']) {
                vi.setSystemTime(new Date(today));
                // 01:30 occurs twice when New York sets its clocks back; the earlier offset, daylight time, applies.
                expect(parseAresDate('2026-11-01 01:30 America/New_York')?.toISOString()).toBe('2026-11-01T05:30:00.000Z');
                // 02:30 is skipped when New York sets its clocks forward; it moves later by the gap, to 03:30 daylight time.
                expect(parseAresDate('2026-03-08 02:30 America/New_York')?.toISOString()).toBe('2026-03-08T07:30:00.000Z');
            }
        } finally {
            vi.useRealTimers();
        }
    });

    it('diffs two files side by side and collapses unchanged lines', () => {
        const before = 'a\nb\nc\nd\ne\nf\n';
        const after = 'a\nb\nc\nd\nE\nf\ng\n';

        const rows = diffLines(before, after);

        expect(rows[0]).toEqual({ kind: 'collapsed', count: 3 });
        expect(rows.find((row) => row.kind === 'changed')).toEqual({ kind: 'changed', left: { number: 5, text: 'e' }, right: { number: 5, text: 'E' } });
        expect(diffStats(rows)).toEqual({ added: 2, removed: 1 });
    });

    it('diffs a large policy with a small edit and bounds a large changed part', () => {
        const lines = Array.from({ length: 10_000 }, (_, index) => `  - "entry${index}"`);
        const edited = lines.map((line, index) => (index === 5_000 ? '  - "changed"' : line));

        const rows = diffLines(lines.join('\n'), edited.join('\n'));
        expect(rows.filter((row) => row.kind === 'changed')).toEqual([
            { kind: 'changed', left: { number: 5_001, text: '  - "entry5000"' }, right: { number: 5_001, text: '  - "changed"' } },
        ]);
        expect(diffStats(rows)).toEqual({ added: 1, removed: 1 });

        const replaced = diffLines(lines.join('\n'), lines.map((line) => line + '!').join('\n'));
        expect(diffStats(replaced)).toEqual({ added: 10_000, removed: 10_000 });

        // Beyond the bound, the lines are compared at the same positions, so two edits of the first and the last line count as two changed lines.
        const ends = lines.map((line, index) => (index === 0 || index === lines.length - 1 ? line + '!' : line));
        const endsAndMiddle = ends.map((line, index) => (index === 5_000 ? line + '?' : line));
        expect(diffStats(diffLines(lines.join('\n'), endsAndMiddle.join('\n')))).toEqual({ added: 3, removed: 3 });
    });
});
