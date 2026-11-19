import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ExampleSubmission, ExampleSubmissionMode } from 'app/assessment/shared/entities/example-submission.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { StringCountService } from 'app/text/overview/service/string-count.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { MockProvider } from 'ng-mocks';

describe('Example Submission Service', () => {
    let service: ExampleSubmissionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: ExerciseService, useClass: MockExerciseService }, MockProvider(StringCountService)],
        });
        service = TestBed.inject(ExampleSubmissionService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('getSubmissionSize', () => {
        it('should return word count for text submission', () => {
            const textExercise = { type: ExerciseType.TEXT } as Exercise;
            const textSubmission = { text: 'hello world test' } as TextSubmission;
            const stringCountService = TestBed.inject(StringCountService);
            vi.spyOn(stringCountService, 'countWords').mockReturnValue(3);

            const result = service.getSubmissionSize(textSubmission, textExercise);

            expect(stringCountService.countWords).toHaveBeenCalledWith('hello world test');
            expect(result).toBe(3);
        });

        it.each([
            {
                shape: 'v3 keyed records',
                model: { version: '3.0.0', elements: { a: { id: 'a' }, b: { id: 'b' } }, relationships: { c: { id: 'c' } } },
            },
            {
                shape: 'v4 arrays under elements/relationships',
                model: { elements: [{ id: 'a' }, { id: 'b' }], relationships: [{ id: 'c' }] },
            },
            {
                shape: 'v5 arrays under nodes/edges',
                model: { version: '5.0.0', nodes: [{ id: 'a' }, { id: 'b' }], edges: [{ id: 'c' }] },
            },
        ])('should return element count for a modeling submission stored as $shape', ({ model }) => {
            const modelingExercise = { type: ExerciseType.MODELING } as Exercise;
            const modelingSubmission = { model: JSON.stringify(model) } as ModelingSubmission;

            expect(service.getSubmissionSize(modelingSubmission, modelingExercise)).toBe(3);
        });

        it.each([
            {
                shape: 'v3',
                model: {
                    version: '3.0.0',
                    elements: { class: { id: 'class' }, attribute: { id: 'attribute' }, method: { id: 'method' } },
                    relationships: { edge: { id: 'edge' } },
                },
            },
            {
                shape: 'v4',
                model: {
                    nodes: [{ id: 'class', data: { attributes: [{ id: 'attribute' }], methods: [{ id: 'method' }] } }],
                    edges: [{ id: 'edge' }],
                },
            },
        ])('should count equivalent $shape models consistently', ({ model }) => {
            const modelingExercise = { type: ExerciseType.MODELING } as Exercise;
            const modelingSubmission = { model: JSON.stringify(model) } as ModelingSubmission;

            expect(service.getSubmissionSize(modelingSubmission, modelingExercise)).toBe(4);
        });

        it('should return 0 for a modeling submission without a model', () => {
            const modelingExercise = { type: ExerciseType.MODELING } as Exercise;

            expect(service.getSubmissionSize({} as ModelingSubmission, modelingExercise)).toBe(0);
        });

        it('should return 0 when submission is undefined', () => {
            const exercise = { type: ExerciseType.TEXT } as Exercise;

            const result = service.getSubmissionSize(undefined, exercise);

            expect(result).toBe(0);
        });

        it('should return 0 when exercise is undefined', () => {
            const submission = { text: 'test' } as TextSubmission;

            const result = service.getSubmissionSize(submission, undefined);

            expect(result).toBe(0);
        });

        it('should return 0 for unsupported exercise types', () => {
            const exercise = { type: ExerciseType.PROGRAMMING } as Exercise;
            const submission = {} as Submission;

            const result = service.getSubmissionSize(submission, exercise);

            expect(result).toBe(0);
        });
    });

    describe('ExampleSubmission model & DTO', () => {
        describe('ExampleSubmission', () => {
            it('allows creating an empty object with all optionals undefined', () => {
                const ex: ExampleSubmission = {};

                expect(ex.id).toBeUndefined();
                expect(ex.usedForTutorial).toBeUndefined();
                expect(ex.exercise).toBeUndefined();
                expect(ex.submission).toBeUndefined();
                expect(ex.tutorParticipations).toBeUndefined();
                expect(ex.assessmentExplanation).toBeUndefined();
            });

            it('supports setting all known fields', () => {
                const ex: ExampleSubmission = {
                    id: 42,
                    usedForTutorial: false,
                    assessmentExplanation: 'why',
                    // these are loose runtime checks; shapes are validated by TS at compile-time
                    exercise: { id: 99 } as any,
                    submission: { id: 7 } as any,
                    tutorParticipations: [{ id: 3 } as any],
                };

                expect(ex.id).toBe(42);
                expect(ex.usedForTutorial).toBe(false);
                expect(ex.assessmentExplanation).toBe('why');
                expect((ex.exercise as any).id).toBe(99);
                expect((ex.submission as any).id).toBe(7);
                expect(ex.tutorParticipations).toHaveLength(1);
            });
        });

        describe('ExampleSubmissionMode enum', () => {
            it('has the expected string values', () => {
                expect(ExampleSubmissionMode.READ_AND_CONFIRM).toBe('readConfirm');
                expect(ExampleSubmissionMode.ASSESS_CORRECTLY).toBe('assessCorrectly');
            });
        });
    });
});
