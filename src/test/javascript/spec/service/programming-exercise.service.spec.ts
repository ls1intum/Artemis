import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { ArtemisTestModule } from '../test.module';
import dayjs from 'dayjs/esm';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { Course } from 'app/entities/course.model';
import { SolutionProgrammingExerciseParticipation } from 'app/entities/participation/solution-programming-exercise-participation.model';
import { Submission } from 'app/entities/submission.model';

describe('ProgrammingExercise Service', () => {
    let service: ProgrammingExerciseService;
    let httpMock: HttpTestingController;

    let defaultProgrammingExercise: ProgrammingExercise;
    const resourceUrl = 'api/programming-exercises';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ProgrammingExerciseService);
                httpMock = TestBed.inject(HttpTestingController);

                defaultProgrammingExercise = new ProgrammingExercise(undefined, undefined);
            });
    });

    describe('Service methods', () => {
        it('should find an exercise', fakeAsync(() => {
            const returnedFromService = {
                ...defaultProgrammingExercise,
                releaseDate: undefined,
                dueDate: undefined,
                assessmentDueDate: undefined,
                buildAndTestStudentSubmissionsAfterDueDate: undefined,
                studentParticipations: [],
            };
            const expected = { ...returnedFromService };
            service
                .find(123)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should create a ProgrammingExercise', fakeAsync(() => {
            const returnedFromService = {
                ...defaultProgrammingExercise,
                id: 0,
                releaseDate: undefined,
                dueDate: undefined,
                assessmentDueDate: undefined,
                buildAndTestStudentSubmissionsAfterDueDate: undefined,
                studentParticipations: [],
            };
            const expected = Object.assign({}, returnedFromService);
            service
                .automaticSetup(new ProgrammingExercise(undefined, undefined))
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should reconnect template submission with result', fakeAsync(() => {
            const templateParticipation = new TemplateProgrammingExerciseParticipation();
            const tempSubmission = new ProgrammingSubmission();
            const tempResult = new Result();
            tempResult.id = 2;
            tempSubmission.results = [tempResult];
            templateParticipation.submissions = [tempSubmission];
            const returnedFromService = {
                ...defaultProgrammingExercise,
                templateParticipation,
                id: 0,
                releaseDate: undefined,
                dueDate: undefined,
                assessmentDueDate: undefined,
                buildAndTestStudentSubmissionsAfterDueDate: undefined,
                studentParticipations: [],
            };
            const expected = Object.assign({}, returnedFromService);
            service
                .findWithTemplateAndSolutionParticipationAndLatestResults(expected.id)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should not find a latest result if none exists', () => {
            const participation = new TemplateProgrammingExerciseParticipation();
            expect(service.getLatestResult(participation)).toBeUndefined();

            participation.submissions = [];
            expect(service.getLatestResult(participation)).toBeUndefined();

            const submission = new ProgrammingSubmission();
            participation.submissions = [submission];
            expect(service.getLatestResult(participation)).toBeUndefined();

            submission.results = [];
            expect(service.getLatestResult(participation)).toBeUndefined();
        });

        it('should find the latest result if multiple exist', () => {
            const participation = new SolutionProgrammingExerciseParticipation();

            const submission1 = {
                submissionDate: dayjs().subtract(1, 'minute'),
                results: [new Result(), new Result()],
            } as Submission;

            const result1 = new Result();
            result1.id = 1;
            const result2 = new Result();
            result2.id = 2;

            const submission2 = {
                submissionDate: dayjs(),
                results: [result1, result2],
            } as Submission;

            // service should find latest submission according to submission date, not order in list
            participation.submissions = [submission2, submission1];

            expect(service.getLatestResult(participation)).toEqual(result2);
        });

        it('should update a ProgrammingExercise', fakeAsync(() => {
            const returnedFromService = {
                ...defaultProgrammingExercise,
                templateRepositoryUrl: 'BBBBBB',
                solutionRepositoryUrl: 'BBBBBB',
                templateBuildPlanId: 'BBBBBB',
                publishBuildPlanUrl: true,
                allowOnlineEditor: true,
                releaseDate: undefined,
                dueDate: undefined,
                assessmentDueDate: undefined,
                buildAndTestStudentSubmissionsAfterDueDate: undefined,
                studentParticipations: [],
            };

            const expected = Object.assign({}, returnedFromService);
            service
                .update(expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update the Timeline of a ProgrammingExercise', fakeAsync(() => {
            const returnedFromService = {
                ...defaultProgrammingExercise,
                releaseDate: dayjs('2020-12-10 10:00:00'),
                dueDate: dayjs('2021-01-01 10:00:00'),
                assessmentDueDate: dayjs('2021-01-02 10:00:00'),
                buildAndTestStudentSubmissionsAfterDueDate: undefined,
                studentParticipations: [],
            };
            const expected = Object.assign({}, returnedFromService);
            service
                .updateTimeline(expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return a list of ProgrammingExercise', fakeAsync(() => {
            const returnedFromService = {
                ...defaultProgrammingExercise,
                templateRepositoryUrl: 'BBBBBB',
                solutionRepositoryUrl: 'BBBBBB',
                templateBuildPlanId: 'BBBBBB',
                publishBuildPlanUrl: true,
                allowOnlineEditor: true,
                releaseDate: undefined,
                dueDate: undefined,
                assessmentDueDate: undefined,
                studentParticipations: [],
            };
            const expected = Object.assign({}, returnedFromService);
            service
                .query(expected)
                .pipe(take(1))
                .subscribe((resp) => {
                    expect(resp.body).toIncludeAllMembers([expected]);
                });
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush([returnedFromService]);
            tick();
        }));

        it('should delete a ProgrammingExercise', fakeAsync(() => {
            service.delete(123, false, false).subscribe((resp) => expect(resp.ok).toBeTrue());

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
        }));

        it('should make get request', fakeAsync(() => {
            const expectedBlob = new Blob(['abc', 'cfe']);
            service.exportInstructorExercise(123).subscribe((resp) => expect(resp.body).toEqual(expectedBlob));
            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/123/export-instructor-exercise` });
            req.flush(expectedBlob);
            tick();
        }));
    });

    it('should make post request for structural solution entries', fakeAsync(() => {
        const expected = [new ProgrammingExerciseSolutionEntry()];
        expected[0].filePath = 'src/test.java';
        service.createStructuralSolutionEntries(123).subscribe((resp) => expect(resp).toEqual(expected));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/123/structural-solution-entries` });
        req.flush(expected);
        tick();
    }));

    it('should make post request for behavioral solution entries', fakeAsync(() => {
        const expected = [new ProgrammingExerciseSolutionEntry()];
        expected[0].filePath = 'src/test.java';
        service.createBehavioralSolutionEntries(123).subscribe((resp) => expect(resp).toEqual(expected));
        const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/123/behavioral-solution-entries` });
        req.flush(expected);
        tick();
    }));

    it('should make post request for import from file', fakeAsync(() => {
        const course = new Course();
        course.id = 1;
        const request = new ProgrammingExercise(course, undefined);
        const expected = new ProgrammingExercise(course, undefined);
        const dummyFile = new File([''], 'dummyFile');
        expected.studentParticipations = [];
        expected.zipFileForImport = dummyFile;
        request.zipFileForImport = dummyFile;
        service.importFromFile(request, course.id).subscribe((resp) => expect(resp.body).toEqual(expected));
        const url = `api/courses/1/programming-exercises/import-from-file`;
        const req = httpMock.expectOne({ method: 'POST', url: url });
        req.flush(request);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
