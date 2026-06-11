import { describe, expect, it } from 'vitest';
import { parseGenerationProgress } from 'app/hyperion/exercise-generation/generation-progress.model';
import { ExerciseGenerationEvent } from 'app/hyperion/services/hyperion-exercise-generation-websocket.service';

function progress(message: string): ExerciseGenerationEvent {
    return { type: 'PROGRESS', message };
}

describe('parseGenerationProgress', () => {
    it('starts in the preparing phase before anything meaningful arrives', () => {
        const result = parseGenerationProgress([{ type: 'STARTED', message: 'Starting exercise generation' }], false);
        expect(result.phase).toBe('preparing');
        expect(result.files).toEqual([]);
    });

    it('moves through preparing, authoring, verifying and saving as the run progresses', () => {
        expect(parseGenerationProgress([progress('Setting up the build environment')], false).phase).toBe('preparing');
        expect(parseGenerationProgress([progress('Turn 1: write_file solution/A.java')], false).phase).toBe('authoring');
        expect(parseGenerationProgress([progress('Checking the exercise builds and grades (attempt 1 of 3)')], false).phase).toBe('verifying');
        expect(parseGenerationProgress([progress('Checks passed. Saving the exercise.')], false).phase).toBe('saving');
    });

    it('forces the done phase once a terminal event has arrived', () => {
        const result = parseGenerationProgress([progress('Turn 3: write_file solution/A.java')], true);
        expect(result.phase).toBe('done');
    });

    it('captures the verification attempt and total', () => {
        const result = parseGenerationProgress([progress('Checking the exercise builds and grades (attempt 2 of 3)')], false);
        expect(result.attempt).toBe(2);
        expect(result.attemptTotal).toBe(3);
    });

    it('collects created and edited files grouped by repository, in first-seen order, deduplicated', () => {
        // The server renders the full path as the file tool's argument (AgentLoopRunner#describeToolCall), so the path is everything after the tool name.
        const result = parseGenerationProgress(
            [
                progress('Turn 1: write_file solution/src/Sorter.java'),
                progress('Turn 2: write_file tests/test/SorterTest.java'),
                progress('Turn 5: edit_file solution/src/Sorter.java'),
                progress('Turn 6: write_file template/src/Sorter.java'),
            ],
            false,
        );
        expect(result.files).toEqual([
            { path: 'solution/src/Sorter.java', repo: 'solution', action: 'create' },
            { path: 'tests/test/SorterTest.java', repo: 'tests', action: 'create' },
            { path: 'template/src/Sorter.java', repo: 'template', action: 'create' },
        ]);
    });

    it('records an edited (not created) file when the first touch is an edit', () => {
        const result = parseGenerationProgress([progress('Turn 4: edit_file problem-statement.md')], false);
        expect(result.files).toEqual([{ path: 'problem-statement.md', repo: 'other', action: 'edit' }]);
    });

    it('parses several newline-joined lines from a single coalesced event', () => {
        const result = parseGenerationProgress(
            [progress('Turn 1: write_file solution/A.java\nTurn 2: bash {"command":"sh verify.sh solution"}\nTurn 3: edit_file tests/T.java')],
            false,
        );
        expect(result.files.map((f) => f.path)).toEqual(['solution/A.java', 'tests/T.java']);
        expect(result.currentStep).toContain('edit_file');
    });

    it('ignores tool calls that are not file writes (bash, submit, read) without breaking the file list', () => {
        const result = parseGenerationProgress([progress('Turn 1: bash {"command":"ls -R"}'), progress('Turn 2: submit {}'), progress('Turn 3: read_file solution/A.java')], false);
        expect(result.files).toEqual([]);
    });

    it('keeps a long, untruncated path intact (the server renders the full path first so it is never cut)', () => {
        const longPath = 'solution/src/de/tum/cit/aet/example/very/deeply/nested/package/structure/VeryLongClassNameThatExceedsAnyReasonableLimit.java';
        const result = parseGenerationProgress([progress(`Turn 1: write_file ${longPath}`)], false);
        expect(result.files[0].path).toBe(longPath);
    });

    it('uses the latest meaningful line as the current step caption', () => {
        const result = parseGenerationProgress(
            [progress('Turn 1: write_file solution/A.java'), progress('Context window under pressure (~60000 tokens); compacting earlier steps.')],
            false,
        );
        expect(result.currentStep).toContain('compacting');
        // an unrecognised line updates the caption but keeps the coarse phase
        expect(result.phase).toBe('authoring');
    });
});
