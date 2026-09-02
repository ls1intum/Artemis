import { describe, expect, it } from 'vitest';

import { ExerciseGenerationRetainedFile } from 'app/openapi/model/exercise-generation-retained-file';
import { ExerciseGenerationFileChange, HyperionFileChangeAction, HyperionFileChangeRepo } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import {
    HyperionArtifactContentContext,
    HyperionArtifactFile,
    artifactContentState,
    artifactFiles,
    artifactKeyForChange,
    artifactRepoGroups,
} from 'app/hyperion/exercise-generation/artifacts/hyperion-artifact-file';

function change(
    repo: HyperionFileChangeRepo,
    path: string,
    { action = 'write' as HyperionFileChangeAction, turn = 1, timestamp = '2026-07-13T09:00:00Z' } = {},
): ExerciseGenerationFileChange {
    return { type: 'FILE_CHANGE', repo, path, action, turn, timestamp };
}

function retained(repo: ExerciseGenerationRetainedFile['repo'], path: string, content: string): ExerciseGenerationRetainedFile {
    return { repo, path, content };
}

const NOTHING: HyperionArtifactContentContext = { loading: false, failed: false, running: false, savedToExercise: false };

function file(overrides: Partial<HyperionArtifactFile> = {}): HyperionArtifactFile {
    return { key: 'k', repo: 'solution', path: 'src/A.java', directory: 'src/', name: 'A.java', mostRecent: false, ...overrides };
}

describe('artifactFiles', () => {
    it('strips the repository prefix so the repository heading is not repeated in every row', () => {
        const [entry] = artifactFiles([change('solution', 'solution/src/de/tum/Loan.java')]);

        expect(entry.path).toBe('src/de/tum/Loan.java');
        expect(entry.directory).toBe('src/de/tum/');
        expect(entry.name).toBe('Loan.java');
    });

    it('keeps a path that does not carry its repository as a prefix untouched', () => {
        const [entry] = artifactFiles([change('tests', 'src/SolutionTest.java')]);

        expect(entry.path).toBe('src/SolutionTest.java');
    });

    it('treats a file at the repository root as having no directory at all', () => {
        const [entry] = artifactFiles([change('other', 'problem-statement.md')]);

        expect(entry.directory).toBe('');
        expect(entry.name).toBe('problem-statement.md');
    });

    it('collapses repeated writes of one file into a single row carrying the last action', () => {
        const files = artifactFiles([
            change('solution', 'solution/src/A.java', { action: 'write', turn: 1 }),
            change('solution', 'solution/src/A.java', { action: 'edit', turn: 4, timestamp: '2026-07-13T09:10:00Z' }),
        ]);

        expect(files).toHaveLength(1);
        expect(files[0].action).toBe('edit');
        expect(files[0].turn).toBe(4);
    });

    it('orders by repository first and then by path, so the list never reshuffles as events arrive', () => {
        const files = artifactFiles([
            change('tests', 'tests/src/BTest.java'),
            change('solution', 'solution/src/Z.java'),
            change('template', 'template/src/A.java'),
            change('solution', 'solution/src/A.java'),
        ]);

        expect(files.map((entry) => `${entry.repo}:${entry.path}`)).toEqual(['solution:src/A.java', 'solution:src/Z.java', 'template:src/A.java', 'tests:src/BTest.java']);
    });

    it('marks exactly one file as the run’s most recent write, by turn and then by timestamp', () => {
        const files = artifactFiles([
            change('solution', 'solution/src/A.java', { turn: 1 }),
            change('tests', 'tests/src/BTest.java', { turn: 5, timestamp: '2026-07-13T09:05:00Z' }),
            change('template', 'template/src/C.java', { turn: 5, timestamp: '2026-07-13T09:04:00Z' }),
        ]);

        expect(files.filter((entry) => entry.mostRecent).map((entry) => entry.name)).toEqual(['BTest.java']);
    });

    it('has no most-recent file when there are no change events at all', () => {
        const files = artifactFiles([], [retained('solution', 'src/A.java', 'class A {}')]);

        expect(files.every((entry) => !entry.mostRecent)).toBe(true);
    });

    it('adds content to a file the change events already know about without losing its action or turn', () => {
        const files = artifactFiles([change('solution', 'solution/src/A.java', { action: 'edit', turn: 3 })], [retained('solution', 'src/A.java', 'class A {}')]);

        expect(files).toHaveLength(1);
        expect(files[0]).toMatchObject({ action: 'edit', turn: 3, content: 'class A {}' });
    });

    it('keeps a retained file whose change event fell out of the replay window', () => {
        const files = artifactFiles([], [retained('tests', 'src/BTest.java', 'class BTest {}')]);

        expect(files.map((entry) => entry.name)).toEqual(['BTest.java']);
        expect(files[0].action).toBeUndefined();
        expect(files[0].content).toBe('class BTest {}');
    });

    it('matches a retained file to its change event even when the snapshot path carries the repository prefix', () => {
        const files = artifactFiles([change('solution', 'solution/src/A.java')], [retained('solution', 'solution/src/A.java', 'class A {}')]);

        expect(files).toHaveLength(1);
        expect(files[0].content).toBe('class A {}');
    });

    it('leaves content undefined rather than empty for a file only the change events know about', () => {
        const [entry] = artifactFiles([change('solution', 'solution/src/A.java')]);

        expect(entry.content).toBeUndefined();
    });

    it('gives every file a key a caller can map back to the change it came from', () => {
        const source = change('solution', 'solution/src/A.java');

        expect(artifactFiles([source])[0].key).toBe(artifactKeyForChange(source));
    });

    it('does not collide the same path in two repositories', () => {
        const files = artifactFiles([change('solution', 'solution/src/A.java'), change('template', 'template/src/A.java')]);

        expect(files).toHaveLength(2);
        expect(new Set(files.map((entry) => entry.key)).size).toBe(2);
    });
});

describe('artifactRepoGroups', () => {
    it('groups in the one repository order every Hyperion surface uses and drops the empty ones', () => {
        const groups = artifactRepoGroups(artifactFiles([change('tests', 'tests/src/BTest.java'), change('solution', 'solution/src/A.java')]));

        expect(groups.map((group) => group.repo)).toEqual(['solution', 'tests']);
        expect(groups.map((group) => group.count)).toEqual([1, 1]);
    });

    it('names each group with the shared repository translation key', () => {
        const [group] = artifactRepoGroups(artifactFiles([change('solution', 'solution/src/A.java')]));

        expect(group.labelKey).toBe('artemisApp.hyperion.generationActivity.repo.solution');
    });

    it('tells a caller which group holds the newest write without it having to look inside', () => {
        const groups = artifactRepoGroups(artifactFiles([change('solution', 'solution/src/A.java', { turn: 1 }), change('tests', 'tests/src/BTest.java', { turn: 9 })]));

        expect(groups.map((group) => [group.repo, group.containsMostRecent])).toEqual([
            ['solution', false],
            ['tests', true],
        ]);
    });

    it('produces nothing at all for an empty workspace', () => {
        expect(artifactRepoGroups([])).toEqual([]);
    });
});

describe('artifactContentState', () => {
    it('reports a deleted file as deleted even when the run is still going', () => {
        expect(artifactContentState(file({ action: 'delete' }), { ...NOTHING, running: true })).toEqual({ kind: 'deleted' });
    });

    it('prefers content that actually arrived over any explanation of why it might not have', () => {
        expect(artifactContentState(file({ content: 'a\nb' }), { loading: true, failed: true, running: true, savedToExercise: true })).toEqual({
            kind: 'text',
            content: 'a\nb',
            lineCount: 2,
        });
    });

    it('distinguishes a real zero-byte file from having no content to show', () => {
        expect(artifactContentState(file({ content: '' }), NOTHING)).toEqual({ kind: 'empty' });
    });

    it('counts a single unterminated line as one line', () => {
        expect(artifactContentState(file({ content: 'only' }), NOTHING)).toEqual({ kind: 'text', content: 'only', lineCount: 1 });
    });

    it('is loading while the retained snapshot is in flight', () => {
        expect(artifactContentState(file(), { ...NOTHING, loading: true })).toEqual({ kind: 'loading' });
    });

    it('reports a failed fetch ahead of the run’s own state, because the failure is the newer fact', () => {
        expect(artifactContentState(file(), { ...NOTHING, failed: true, running: true })).toEqual({ kind: 'failed' });
    });

    it('says the contents are not servable yet while the run is still going', () => {
        expect(artifactContentState(file(), { ...NOTHING, running: true })).toEqual({ kind: 'pendingRun' });
    });

    it('sends the reader to the repository once the run has saved its work', () => {
        expect(artifactContentState(file(), { ...NOTHING, savedToExercise: true })).toEqual({ kind: 'savedToExercise' });
    });

    it('says the run kept nothing when it neither saved nor retained', () => {
        expect(artifactContentState(file(), NOTHING)).toEqual({ kind: 'notRetained' });
    });
});
