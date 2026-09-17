import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { ExerciseReferenceAction } from 'app/editor/monaco-editor/model/actions/communication/exercise-reference.action';
import { MetisService } from 'app/communication/service/metis.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

/**
 * The completion provider reads its options through {@link ExerciseReferenceAction.loadTitles}, so what matters is that
 * a user who types `/exercise` gets the exercises even if the request has not landed yet, that concurrent invocations
 * share one request, and that a failure does not leave the editor permanently empty.
 */
describe('ExerciseReferenceAction', () => {
    let exerciseService: ExerciseService;
    let metisService: MetisService;
    let getTitles: ReturnType<typeof vi.fn>;

    /** loadTitles is private; the completion provider is the only production caller and reaches it the same way. */
    const loadTitles = (action: ExerciseReferenceAction): Promise<{ id: string; value: string }[]> => (action as any).loadTitles();

    beforeEach(() => {
        getTitles = vi.fn().mockReturnValue(of([{ id: 1, title: 'Sorting', type: ExerciseType.PROGRAMMING }]));
        exerciseService = { getTitlesForCourse: getTitles } as unknown as ExerciseService;
        metisService = { getCourse: () => ({ id: 7 }) } as unknown as MetisService;
    });

    it('should resolve the exercises even when they are requested before the response lands', async () => {
        const action = new ExerciseReferenceAction(metisService, exerciseService);

        const values = await loadTitles(action);

        expect(values).toEqual([{ id: '1', value: 'Sorting', type: ExerciseType.PROGRAMMING }]);
    });

    it('should ask the server once and share the result between concurrent invocations', async () => {
        const action = new ExerciseReferenceAction(metisService, exerciseService);

        const [first, second] = await Promise.all([loadTitles(action), loadTitles(action)]);

        expect(getTitles).toHaveBeenCalledExactlyOnceWith(7);
        expect(first).toEqual(second);
    });

    it('should skip exercises without a title, which cannot be referenced', async () => {
        getTitles.mockReturnValue(
            of([
                { id: 1, title: undefined },
                { id: 2, title: 'Named' },
            ]),
        );
        const action = new ExerciseReferenceAction(metisService, exerciseService);

        const values = await loadTitles(action);

        expect(values).toEqual([{ id: '2', value: 'Named', type: undefined }]);
    });

    it('should retry after a failure rather than leaving the editor empty for the rest of the session', async () => {
        getTitles.mockReturnValueOnce(throwError(() => new Error('offline')));
        const action = new ExerciseReferenceAction(metisService, exerciseService);

        await expect(loadTitles(action)).resolves.toEqual([]);

        const retried = await loadTitles(action);

        expect(getTitles).toHaveBeenCalledTimes(2);
        expect(retried).toEqual([{ id: '1', value: 'Sorting', type: ExerciseType.PROGRAMMING }]);
    });

    it('should not ask the server until the completion provider needs the exercises', () => {
        new ExerciseReferenceAction(metisService, exerciseService);

        expect(getTitles).not.toHaveBeenCalled();
    });
});
