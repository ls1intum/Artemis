import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ExampleSubmission, ExampleSubmissionDTO, ExampleSubmissionMode } from 'app/assessment/shared/entities/example-submission.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Submission, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { StringCountService } from 'app/text/overview/service/string-count.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { MockProvider } from 'ng-mocks';

describe('Example Submission Service', () => {
    let httpMock: HttpTestingController;
    let service: ExampleSubmissionService;
    let expectedResult: any;
    let elemDefault: ExampleSubmission;
    let studentSubmission: Submission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ExerciseService, useClass: MockExerciseService }, MockProvider(StringCountService)],
        });
        service = TestBed.inject(ExampleSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);

        expectedResult = {} as HttpResponse<ExampleSubmission[]>;
        elemDefault = new ExampleSubmission();
        elemDefault.id = 1;
        elemDefault.usedForTutorial = false;
        elemDefault.exercise = {
            id: 1,
            problemStatement: 'problem statement',
            title: 'title',
            shortName: 'titleShort',
        } as unknown as Exercise;
        elemDefault.submission = {
            id: 1,
            submitted: true,
            type: 'AUTOMATIC',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as TextSubmission;
        elemDefault.submission.results = [
            {
                id: 2374,
                score: 8,
                rated: true,
                hasComplaint: false,
            } as unknown as Result,
        ];
        getLatestSubmissionResult(elemDefault.submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        elemDefault.assessmentExplanation = 'exampleSubmissionTest';
        studentSubmission = elemDefault.submission;
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    describe('Service methods', () => {
        it('should create an example submission', fakeAsync(() => {
            const exerciseId = 1;
            const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
            const expected = Object.assign({}, returnedFromService);
            service
                .create(elemDefault, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).toEqual(expected);
        }));

        it('should update an example submission', fakeAsync(() => {
            const exerciseId = 1;
            const returnedFromService = Object.assign({}, elemDefault);
            const expected = Object.assign({}, returnedFromService);
            service
                .update(elemDefault, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).toEqual(expected);
        }));

        it('should delete an example submission', fakeAsync(() => {
            const exampleSubmissionId = 1;
            service.delete(exampleSubmissionId).subscribe((resp) => (expectedResult = resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
            expect(expectedResult).toBeTrue();
        }));

        it('should return an example submission', fakeAsync(() => {
            const exampleSubmissionId = 1;
            const returnedFromService = Object.assign({}, elemDefault);
            const expected = Object.assign({}, returnedFromService);
            service
                .get(exampleSubmissionId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).toEqual(expected);
        }));

        it('should import an example submission', fakeAsync(() => {
            const exerciseId = 1;
            const returnedFromService = Object.assign({}, elemDefault);
            const expected = Object.assign({}, returnedFromService);
            service
                .import(studentSubmission.id!, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).toEqual(expected);
        }));
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
                expect(ex.usedForTutorial).toBeFalse();
                expect(ex.assessmentExplanation).toBe('why');
                expect((ex.exercise as any).id).toBe(99);
                expect((ex.submission as any).id).toBe(7);
                expect(ex.tutorParticipations).toHaveLength(1);
            });
        });

        describe('ExampleSubmissionDTO', () => {
            it('constructor assigns all fields', () => {
                const dto = new ExampleSubmissionDTO(1, true, 777, 'note');

                expect(dto.id).toBe(1);
                expect(dto.usedForTutorial).toBeTrue();
                expect(dto.submissionId).toBe(777);
                expect(dto.assessmentExplanation).toBe('note');
            });

            it('constructor works without assessmentExplanation', () => {
                const dto = new ExampleSubmissionDTO(2, false, 888);

                expect(dto.id).toBe(2);
                expect(dto.usedForTutorial).toBeFalse();
                expect(dto.submissionId).toBe(888);
                expect(dto.assessmentExplanation).toBeUndefined();
            });

            it('serializes to JSON with the expected shape', () => {
                const dto = new ExampleSubmissionDTO(3, true, 999, 'x');
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
