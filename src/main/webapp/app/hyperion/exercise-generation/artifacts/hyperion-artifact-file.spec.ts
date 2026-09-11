import { describe, expect, it } from 'vitest';

import { ExerciseGenerationFileChange, HyperionFileChangeAction, HyperionFileChangeRepo } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { artifactFiles, artifactKeyForChange, artifactRepoGroups } from 'app/hyperion/exercise-generation/artifacts/hyperion-artifact-file';

function change(
    repo: HyperionFileChangeRepo,
    path: string,
    { action = 'write' as HyperionFileChangeAction, turn = 1, timestamp = '2026-07-13T09:00:00Z' } = {},
): ExerciseGenerationFileChange {
    return { type: 'FILE_CHANGE', repo, path, action, turn, timestamp };
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
