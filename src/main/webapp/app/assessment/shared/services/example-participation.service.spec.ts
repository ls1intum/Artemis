import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { ExampleParticipationService } from 'app/assessment/shared/services/example-participation.service';
import { ExampleParticipation, ExampleParticipationDTO, ExampleSubmissionMode } from 'app/exercise/shared/entities/participation/example-participation.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Submission, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { StringCountService } from 'app/text/overview/service/string-count.service';
import { MockProvider } from 'ng-mocks';

describe('Example Participation Service', () => {
    setupTestBed({ zoneless: true });
    let httpMock: HttpTestingController;
    let service: ExampleParticipationService;
    let expectedResult: any;
    let elemDefault: ExampleParticipation;
    let studentSubmission: Submission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), MockProvider(StringCountService)],
        });
        service = TestBed.inject(ExampleParticipationService);
        httpMock = TestBed.inject(HttpTestingController);

        expectedResult = {} as HttpResponse<ExampleParticipation[]>;
        elemDefault = new ExampleParticipation();
        elemDefault.id = 1;
        elemDefault.usedForTutorial = false;
        elemDefault.exercise = {
            id: 1,
            problemStatement: 'problem statement',
            title: 'title',
            shortName: 'titleShort',
        } as unknown as Exercise;
        const submission = {
            id: 1,
            submitted: true,
            type: 'AUTOMATIC',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as TextSubmission;
        submission.results = [
            {
                id: 2374,
                score: 8,
                rated: true,
                hasComplaint: false,
            } as unknown as Result,
        ];
        getLatestSubmissionResult(submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        elemDefault.submissions = [submission];
        elemDefault.assessmentExplanation = 'exampleParticipationTest';
        studentSubmission = submission;
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('Service methods', () => {
        it('should create an example participation', () => {
            const exerciseId = 1;
            const returnedFromService = { ...elemDefault, id: 0 };
            const expected = { ...returnedFromService };
            service
                .create(elemDefault, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);

            expect(expectedResult.body).toEqual(expected);
        });

        it('should update an example participation', () => {
            const exerciseId = 1;
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .update(elemDefault, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);

            expect(expectedResult.body).toEqual(expected);
        });

        it('should delete an example participation', () => {
            const exampleParticipationId = 1;
            service.delete(exampleParticipationId).subscribe((resp) => (expectedResult = resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });

            expect(expectedResult).toBe(true);
        });

        it('should return an example participation', () => {
            const exampleParticipationId = 1;
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .get(exampleParticipationId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);

            expect(expectedResult.body).toEqual(expected);
        });

        it('should import an example participation', () => {
            const exerciseId = 1;
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .import(studentSubmission.id!, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);

            expect(expectedResult.body).toEqual(expected);
        });

        it('should get submission from example participation', () => {
            const submission = service.getSubmission(elemDefault);
            expect(submission).toBeDefined();
            expect(submission?.id).toBe(1);
        });

        it('should return undefined when no submissions', () => {
            const emptyParticipation = new ExampleParticipation();
            const submission = service.getSubmission(emptyParticipation);
            expect(submission).toBeUndefined();
        });

        it('should prepare for assessment', () => {
            const exerciseId = 1;
            const exampleParticipationId = 2;
            service
                .prepareForAssessment(exerciseId, exampleParticipationId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp.ok));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush({});

            expect(expectedResult).toBe(true);
        });
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

        it('should return element count for modeling submission', () => {
            const modelingExercise = { type: ExerciseType.MODELING } as Exercise;
            const modelingSubmission = {
                model: JSON.stringify({
                    elements: [{ id: 1 }, { id: 2 }],
                    relationships: [{ id: 3 }],
                }),
            } as ModelingSubmission;

            const result = service.getSubmissionSize(modelingSubmission, modelingExercise);

            expect(result).toBe(3);
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

    describe('ExampleParticipation model & DTO', () => {
        describe('ExampleParticipation', () => {
            it('allows creating an empty object', () => {
                const ex = new ExampleParticipation();

                expect(ex.id).toBeUndefined();
                expect(ex.usedForTutorial).toBeUndefined();
                expect(ex.exercise).toBeUndefined();
                expect(ex.submissions).toBeUndefined();
                expect(ex.tutorParticipations).toBeUndefined();
                expect(ex.assessmentExplanation).toBeUndefined();
            });

            it('supports setting all known fields', () => {
                const ex = new ExampleParticipation();
                ex.id = 42;
                ex.usedForTutorial = false;
                ex.assessmentExplanation = 'why';
                ex.exercise = { id: 99 } as any;
                ex.submissions = [{ id: 7 } as any];
                ex.tutorParticipations = [{ id: 3 } as any];

                expect(ex.id).toBe(42);
                expect(ex.usedForTutorial).toBe(false);
                expect(ex.assessmentExplanation).toBe('why');
                expect((ex.exercise as any).id).toBe(99);
                expect(ex.submissions).toHaveLength(1);
                expect(ex.tutorParticipations).toHaveLength(1);
            });
        });

        describe('ExampleParticipationDTO', () => {
            it('constructor assigns all fields', () => {
                const dto = new ExampleParticipationDTO(1, true, 777, 'note');

                expect(dto.id).toBe(1);
                expect(dto.usedForTutorial).toBe(true);
                expect(dto.submissionId).toBe(777);
                expect(dto.assessmentExplanation).toBe('note');
            });

            it('constructor works without assessmentExplanation', () => {
                const dto = new ExampleParticipationDTO(2, false, 888);

                expect(dto.id).toBe(2);
                expect(dto.usedForTutorial).toBe(false);
                expect(dto.submissionId).toBe(888);
                expect(dto.assessmentExplanation).toBeUndefined();
            });

            it('serializes to JSON with the expected shape', () => {
                const dto = new ExampleParticipationDTO(3, true, 999, 'x');
                const json = JSON.parse(JSON.stringify(dto));

                expect(json).toEqual({
                    id: 3,
                    usedForTutorial: true,
                    submissionId: 999,
                    assessmentExplanation: 'x',
                });
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
