import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { toUpdateTextExerciseDTO } from './update-text-exercise-dto.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { DifficultyLevel, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';

describe('UpdateTextExerciseDTO', () => {
    describe('toUpdateTextExerciseDTO', () => {
        beforeEach(() => {
            vi.spyOn(ExerciseService, 'setBonusPointsConstrainedByIncludedInOverallScore').mockImplementation((exercise) => exercise);
            vi.spyOn(ExerciseService, 'stringifyExerciseDTOCategories').mockReturnValue(['category1']);
        });

        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should convert a text exercise to an update DTO', () => {
            const releaseDate = dayjs('2024-01-01T10:00:00');
            const dueDate = dayjs('2024-01-15T23:59:00');

            const exercise = {
                id: 1,
                title: 'Text Exercise',
                shortName: 'TE',
                maxPoints: 10,
                bonusPoints: 2,
                releaseDate,
                dueDate,
                difficulty: DifficultyLevel.MEDIUM,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                problemStatement: 'Write something',
                gradingInstructions: 'Grade carefully',
                presentationScoreEnabled: false,
                secondCorrectionEnabled: true,
                channelName: 'text-channel',
                exampleSolution: 'Example solution text',
                course: { id: 5 },
                competencyLinks: [{ competency: { id: 10 }, weight: 1 }],
            } as any as TextExercise;

            const dto = toUpdateTextExerciseDTO(exercise);

            expect(dto.id).toBe(1);
            expect(dto.title).toBe('Text Exercise');
            expect(dto.shortName).toBe('TE');
            expect(dto.maxPoints).toBe(10);
            expect(dto.bonusPoints).toBe(2);
            expect(dto.releaseDate).toBe(releaseDate.toJSON());
            expect(dto.dueDate).toBe(dueDate.toJSON());
            expect(dto.difficulty).toBe(DifficultyLevel.MEDIUM);
            expect(dto.problemStatement).toBe('Write something');
            expect(dto.exampleSolution).toBe('Example solution text');
            expect(dto.courseId).toBe(5);
            expect(dto.exerciseGroupId).toBeUndefined();
            expect(dto.competencyLinks).toHaveLength(1);
            expect(dto.competencyLinks![0].competency.id).toBe(10);
            expect(dto.categories).toEqual(['category1']);
        });

        it('should set exerciseGroupId for exam exercises', () => {
            const exercise = {
                id: 2,
                maxPoints: 5,
                exerciseGroup: { id: 99 },
                course: { id: 5 },
            } as any as TextExercise;

            const dto = toUpdateTextExerciseDTO(exercise);

            expect(dto.exerciseGroupId).toBe(99);
            expect(dto.courseId).toBeUndefined();
        });
    });
});
