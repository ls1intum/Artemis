import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { TemplateProgrammingExerciseParticipation } from 'app/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import dayjs from 'dayjs/esm';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../test.module';

describe('ProgrammingExercise Service', () => {
    let service: ProgrammingExerciseService;
    let httpMock: HttpTestingController;
    let defaultProgrammingExercise: ProgrammingExercise;
    const resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    beforeAll(() => {
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
            const returnedFromService = Object.assign(
                {
                    releaseDate: undefined,
                    dueDate: undefined,
                    assessmentDueDate: undefined,
                    buildAndTestStudentSubmissionsAfterDueDate: undefined,
                    studentParticipations: [],
                },
                defaultProgrammingExercise,
            );
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
            const returnedFromService = Object.assign(
                {
                    id: 0,
                    releaseDate: undefined,
                    dueDate: undefined,
                    assessmentDueDate: undefined,
                    buildAndTestStudentSubmissionsAfterDueDate: undefined,
                    studentParticipations: [],
                },
                defaultProgrammingExercise,
            );
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
            const returnedFromService = Object.assign(
                {
                    id: 0,
                    releaseDate: undefined,
                    dueDate: undefined,
                    assessmentDueDate: undefined,
                    buildAndTestStudentSubmissionsAfterDueDate: undefined,
                    studentParticipations: [],
                },
                { ...defaultProgrammingExercise, templateParticipation },
            );
            const expected = Object.assign({}, returnedFromService);
            service
                .findWithTemplateAndSolutionParticipation(expected.id, true)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a ProgrammingExercise', fakeAsync(() => {
            const returnedFromService = Object.assign(
                {
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
                },
                defaultProgrammingExercise,
            );

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
            const returnedFromService = Object.assign(
                {
                    releaseDate: dayjs('2020-12-10 10:00:00'),
                    dueDate: dayjs('2021-01-01 10:00:00'),
                    assessmentDueDate: dayjs('2021-01-02 10:00:00'),
                    buildAndTestStudentSubmissionsAfterDueDate: undefined,
                    studentParticipations: [],
                },
                defaultProgrammingExercise,
            );
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
            const returnedFromService = Object.assign(
                {
                    templateRepositoryUrl: 'BBBBBB',
                    solutionRepositoryUrl: 'BBBBBB',
                    templateBuildPlanId: 'BBBBBB',
                    publishBuildPlanUrl: true,
                    allowOnlineEditor: true,
                    releaseDate: undefined,
                    dueDate: undefined,
                    assessmentDueDate: undefined,
                    studentParticipations: [],
                },
                defaultProgrammingExercise,
            );
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

    afterEach(() => {
        httpMock.verify();
    });
});
