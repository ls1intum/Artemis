import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import dayjs from 'dayjs/esm';
import { vi } from 'vitest';
import { ExerciseGroupSyncService } from 'app/course/manage/exercises/exercise-group-sync.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { ExerciseVariantGroupDTO } from 'app/course/manage/exercises/exercise-variant-group.service';
import { MockProvider } from 'ng-mocks';

describe('ExerciseGroupSyncService', () => {
    setupTestBed({ zoneless: true });

    let service: ExerciseGroupSyncService;
    let quizExerciseService: QuizExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [MockProvider(QuizExerciseService)] });
        service = TestBed.inject(ExerciseGroupSyncService);
        quizExerciseService = TestBed.inject(QuizExerciseService);
    });

    describe('applyQuizClientState', () => {
        it('sets status and quizStarted from the quiz exercise service', () => {
            vi.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.ACTIVE);
            const quiz = { id: 1 } as QuizExercise;

            service.applyQuizClientState(quiz);

            expect(quiz.status).toBe(QuizStatus.ACTIVE);
            expect(quiz.quizStarted).toBe(true);
        });

        it('sets quizStarted to false when the status is not active', () => {
            vi.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.VISIBLE);
            const quiz = { id: 1 } as QuizExercise;

            service.applyQuizClientState(quiz);

            expect(quiz.quizStarted).toBe(false);
        });
    });

    describe('applyGroupTimelineToMember', () => {
        const groupDto: ExerciseVariantGroupDTO = {
            id: 5,
            releaseDate: dayjs('2026-01-01T00:00:00Z'),
            startDate: dayjs('2026-01-02T00:00:00Z'),
            dueDate: dayjs('2026-01-10T00:00:00Z'),
            assessmentDueDate: dayjs('2026-01-15T00:00:00Z'),
            exampleSolutionPublicationDate: dayjs('2026-01-20T00:00:00Z'),
        };

        it('returns a new exercise object carrying the group timeline', () => {
            const exercise = { id: 1, type: ExerciseType.TEXT, dueDate: dayjs('2020-01-01') } as Exercise;

            const updated = service.applyGroupTimelineToMember(exercise, groupDto, dayjs('2026-01-05T00:00:00Z'));

            expect(updated).not.toBe(exercise);
            expect(updated.releaseDate).toBe(groupDto.releaseDate);
            expect(updated.startDate).toBe(groupDto.startDate);
            expect(updated.dueDate).toBe(groupDto.dueDate);
            expect(updated.assessmentDueDate).toBe(groupDto.assessmentDueDate);
            expect(updated.exampleSolutionPublicationDate).toBe(groupDto.exampleSolutionPublicationDate);
        });

        it('recomputes quiz timeline flags for a quiz member', () => {
            vi.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.VISIBLE);
            const quiz = { id: 1, type: ExerciseType.QUIZ } as QuizExercise;

            const updated = service.applyGroupTimelineToMember(quiz, groupDto, dayjs('2026-01-05T00:00:00Z')) as QuizExercise;

            expect(updated.visibleToStudents).toBe(true);
            expect(updated.quizEnded).toBe(false);
            expect(updated.status).toBe(QuizStatus.VISIBLE);
        });

        it('does not touch quiz flags for non-quiz members', () => {
            const exercise = { id: 1, type: ExerciseType.TEXT } as Exercise;

            const updated = service.applyGroupTimelineToMember(exercise, groupDto, dayjs());

            expect((updated as QuizExercise).visibleToStudents).toBeUndefined();
        });
    });

    describe('mergeGroupsIntoExercises', () => {
        it('assigns a new group reference and timeline to exercises newly added to a group', () => {
            const exercise = { id: 1, type: ExerciseType.TEXT, dueDate: dayjs('2020-01-01') } as Exercise;
            const dto: ExerciseVariantGroupDTO = { id: 10, title: 'Group', dueDate: dayjs('2026-02-02T00:00:00Z'), exerciseIds: [1] };

            const result = service.mergeGroupsIntoExercises([exercise], [dto]);

            expect(result.exercises[0]).not.toBe(exercise);
            expect(result.exercises[0].exerciseVariantGroup?.id).toBe(10);
            expect(result.exercises[0].dueDate).toBe(dto.dueDate);
            expect(result.groups).toHaveLength(1);
            expect(result.groups[0].id).toBe(10);
            expect(result.groups[0].exercises?.map((e) => e.id)).toEqual([1]);
        });

        it('drops the group reference but keeps the exercise dates when removed from its group', () => {
            const exercise = {
                id: 1,
                type: ExerciseType.TEXT,
                dueDate: dayjs('2020-01-01'),
                exerciseVariantGroup: { id: 10, title: 'Group' },
            } as Exercise;

            const result = service.mergeGroupsIntoExercises([exercise], []);

            expect(result.exercises[0].exerciseVariantGroup).toBeUndefined();
            expect(result.exercises[0].dueDate).toBe(exercise.dueDate);
        });

        it('leaves an exercise untouched (same reference) when its group membership is unchanged', () => {
            const exercise = { id: 1, type: ExerciseType.TEXT, exerciseVariantGroup: { id: 10 } } as Exercise;
            const dto: ExerciseVariantGroupDTO = { id: 10, exerciseIds: [1] };

            const result = service.mergeGroupsIntoExercises([exercise], [dto]);

            expect(result.exercises[0]).toBe(exercise);
        });

        it('skips exercises without an id', () => {
            const draft = { type: ExerciseType.TEXT } as Exercise;

            const result = service.mergeGroupsIntoExercises([draft], []);

            expect(result.exercises[0]).toBe(draft);
        });
    });

    describe('mergeQuizInfo', () => {
        it('returns undefined when no quiz matches any exercise', () => {
            const exercise = { id: 1, type: ExerciseType.TEXT } as Exercise;

            expect(service.mergeQuizInfo([exercise], [], [])).toBeUndefined();
        });

        it('replaces matching quiz exercises with fresh objects carrying quizBatches and isEditable, and propagates into groups', () => {
            vi.spyOn(quizExerciseService, 'getStatus').mockReturnValue(QuizStatus.ACTIVE);
            const quiz = { id: 2, type: ExerciseType.QUIZ } as QuizExercise;
            const otherExercise = { id: 3, type: ExerciseType.TEXT } as Exercise;
            const loadedQuiz = { id: 2, type: ExerciseType.QUIZ, quizBatches: [{ id: 99, started: true }], isEditable: false } as QuizExercise;
            const group: CourseExerciseGroup = { id: 1, exercises: [quiz] };

            const result = service.mergeQuizInfo([quiz, otherExercise], [group], [loadedQuiz]);

            expect(result).toBeDefined();
            const updatedQuiz = result!.exercises[0] as QuizExercise;
            expect(updatedQuiz).not.toBe(quiz);
            expect(updatedQuiz.quizBatches).toEqual(loadedQuiz.quizBatches);
            expect(updatedQuiz.isEditable).toBe(false);
            expect(updatedQuiz.status).toBe(QuizStatus.ACTIVE);
            expect(result!.exercises[1]).toBe(otherExercise);
            expect(result!.groups[0].exercises?.[0]).toBe(updatedQuiz);
        });
    });
});
