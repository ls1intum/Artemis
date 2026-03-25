/* eslint-disable jest-extended/prefer-to-be-true, jest-extended/prefer-to-be-false */
import dayjs from 'dayjs/esm';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { DifficultyLevel, ExerciseMode, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { UpdateTextExerciseDTO, toUpdateTextExerciseDTO } from './update-text-exercise-dto.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';

describe('UpdateTextExerciseDTO Model', () => {
    const baseDate = dayjs('2025-06-01T10:00:00.000Z');

    describe('toUpdateTextExerciseDTO', () => {
        it('should convert a full text exercise to an update DTO', () => {
            const exercise = {
                id: 7,
                title: 'Essay Exercise',
                shortName: 'essay',
                maxPoints: 50,
                bonusPoints: 5,
                assessmentType: AssessmentType.MANUAL,
                releaseDate: baseDate,
                startDate: baseDate.add(1, 'day'),
                dueDate: baseDate.add(7, 'days'),
                assessmentDueDate: baseDate.add(14, 'days'),
                exampleSolutionPublicationDate: baseDate.add(21, 'days'),
                difficulty: DifficultyLevel.MEDIUM,
                mode: ExerciseMode.INDIVIDUAL,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                problemStatement: 'Write an essay',
                gradingInstructions: 'Check grammar',
                categories: [new ExerciseCategory('writing', '#ff0000')],
                presentationScoreEnabled: true,
                secondCorrectionEnabled: false,
                feedbackSuggestionModule: 'athena',
                allowComplaintsForAutomaticAssessments: false,
                allowFeedbackRequests: true,
                channelName: 'essay-channel',
                competencyLinks: [
                    {
                        competency: { id: 200, title: 'Writing Skills', course: { id: 1 } },
                        weight: 0.5,
                    },
                ],
                course: { id: 1 },
                exampleSolution: 'This is an example solution.',
            } as TextExercise;

            const dto: UpdateTextExerciseDTO = toUpdateTextExerciseDTO(exercise);

            expect(dto.id).toBe(7);
            expect(dto.title).toBe('Essay Exercise');
            expect(dto.shortName).toBe('essay');
            expect(dto.maxPoints).toBe(50);
            expect(dto.bonusPoints).toBe(5);
            expect(dto.releaseDate).toBe(baseDate.toJSON());
            expect(dto.startDate).toBe(baseDate.add(1, 'day').toJSON());
            expect(dto.dueDate).toBe(baseDate.add(7, 'days').toJSON());
            expect(dto.assessmentDueDate).toBe(baseDate.add(14, 'days').toJSON());
            expect(dto.exampleSolutionPublicationDate).toBe(baseDate.add(21, 'days').toJSON());
            expect(dto.difficulty).toBe(DifficultyLevel.MEDIUM);
            expect(dto.includedInOverallScore).toBe(IncludedInOverallScore.INCLUDED_COMPLETELY);
            expect(dto.problemStatement).toBe('Write an essay');
            expect(dto.gradingInstructions).toBe('Check grammar');
            expect(dto.categories).toBeDefined();
            expect(dto.presentationScoreEnabled).toBe(true);
            expect(dto.secondCorrectionEnabled).toBe(false);
            expect(dto.feedbackSuggestionModule).toBe('athena');
            expect(dto.allowComplaintsForAutomaticAssessments).toBe(false);
            expect(dto.allowFeedbackRequests).toBe(true);
            expect(dto.channelName).toBe('essay-channel');
            expect(dto.competencyLinks).toBeDefined();
            expect(dto.competencyLinks).toHaveLength(1);
            expect(dto.courseId).toBe(1);
            expect(dto.exerciseGroupId).toBeUndefined();
            expect(dto.exampleSolution).toBe('This is an example solution.');
        });

        it('should set exerciseGroupId for exam exercises and exclude courseId', () => {
            const examExercise = {
                id: 20,
                title: 'Exam Text',
                maxPoints: 30,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                exerciseGroup: { id: 55 },
                course: { id: 1 },
            } as TextExercise;

            const dto = toUpdateTextExerciseDTO(examExercise);

            expect(dto.exerciseGroupId).toBe(55);
            expect(dto.courseId).toBeUndefined();
        });

        it('should set bonusPoints to 0 when includedInOverallScore is not INCLUDED_COMPLETELY', () => {
            const exercise = {
                id: 1,
                title: 'No Bonus Text',
                maxPoints: 20,
                bonusPoints: 10,
                includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
                course: { id: 1 },
            } as TextExercise;

            const dto = toUpdateTextExerciseDTO(exercise);

            expect(dto.bonusPoints).toBe(0);
        });

        it('should handle undefined optional fields', () => {
            const minimalExercise = {
                id: 1,
                title: 'Minimal',
                maxPoints: 10,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                course: { id: 1 },
            } as TextExercise;

            const dto = toUpdateTextExerciseDTO(minimalExercise);

            expect(dto.releaseDate).toBeUndefined();
            expect(dto.startDate).toBeUndefined();
            expect(dto.dueDate).toBeUndefined();
            expect(dto.assessmentDueDate).toBeUndefined();
            expect(dto.exampleSolutionPublicationDate).toBeUndefined();
            expect(dto.competencyLinks).toEqual([]);
            expect(dto.exampleSolution).toBeUndefined();
        });

        it('should convert categories to JSON strings', () => {
            const exercise = {
                id: 1,
                title: 'Categorized Text',
                maxPoints: 10,
                categories: [new ExerciseCategory('essay', '#ff0000')],
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                course: { id: 1 },
            } as TextExercise;

            const dto = toUpdateTextExerciseDTO(exercise);

            expect(dto.categories).toBeDefined();
            expect(dto.categories!).toHaveLength(1);
        });
    });
});
