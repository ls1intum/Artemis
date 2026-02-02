import { describe, expect, it } from 'vitest';

import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { createExerciseMetadataHandlers } from 'app/exercise/synchronization/exercise-metadata-handlers';

const extractKeys = (handlers: { key: string }[]) => handlers.map((handler) => handler.key);

describe('ExerciseMetadataHandlers', () => {
    it('includes base fields for all exercise types', () => {
        const handlers = createExerciseMetadataHandlers(ExerciseType.TEXT);
        const keys = extractKeys(handlers);

        expect(keys).toContain('title');
        expect(keys).toContain('shortName');
        expect(keys).toContain('competencyLinks');
        expect(keys).toContain('channelName');
        expect(keys).toContain('maxPoints');
        expect(keys).toContain('problemStatement');
    });

    it('includes type-specific fields', () => {
        const textHandlers = createExerciseMetadataHandlers(ExerciseType.TEXT);
        const programmingHandlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING);

        expect(extractKeys(textHandlers)).toContain('textData.exampleSolution');
        expect(extractKeys(programmingHandlers)).toContain('programmingData.testRepositoryUri');
    });
});
