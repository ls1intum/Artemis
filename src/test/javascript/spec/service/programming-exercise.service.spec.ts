import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import dayjs from 'dayjs/esm';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { ProgrammingExerciseGitDiffReport } from 'app/programming/shared/entities/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseGitDiffEntry } from 'app/programming/shared/entities/programming-exercise-git-diff-entry.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { provideHttpClient } from '@angular/common/http';
import { RepositoryType } from '../../../../main/webapp/app/programming/shared/code-editor/model/code-editor.model';

describe('ProgrammingExercise Service', () => {
    let service: ProgrammingExerciseService;
    let httpMock: HttpTestingController;

    let defaultProgrammingExercise: ProgrammingExercise;
    const resourceUrl = 'api/programming/programming-exercises';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
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

        it('should find with template and solution participation and results', fakeAsync(() => {
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
                .findWithTemplateAndSolutionParticipation(123)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should find with auxiliary repositories', fakeAsync(() => {
            const auxiliaryRepository: AuxiliaryRepository = { id: 5 };
            const returnedFromService = {
                ...defaultProgrammingExercise,
                auxiliaryRepositories: [auxiliaryRepository],
                releaseDate: undefined,
                dueDate: undefined,
                assessmentDueDate: undefined,
                buildAndTestStudentSubmissionsAfterDueDate: undefined,
                studentParticipations: [],
            };
            const expected = { ...returnedFromService };
            service
                .findWithAuxiliaryRepository(returnedFromService.id!)
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
                templateRepositoryUri: 'BBBBBB',
                solutionRepositoryUri: 'BBBBBB',
                templateBuildPlanId: 'BBBBBB',
                allowOnlineEditor: true,
                allowOnlineIde: true,
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
                templateRepositoryUri: 'BBBBBB',
                solutionRepositoryUri: 'BBBBBB',
                templateBuildPlanId: 'BBBBBB',
                allowOnlineEditor: true,
                allowOnlineIde: true,
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
        const url = `api/programming/courses/1/programming-exercises/import-from-file`;
        const req = httpMock.expectOne({ method: 'POST', url: url });
        req.flush(request);
        tick();
    }));

    it('should make GET request to retrieve diff between submission and template', fakeAsync(() => {
        const exerciseId = 1;
        const submissionId = 2;
        const expected = { id: 1, entries: [] } as unknown as ProgrammingExerciseGitDiffReport;
        service.getDiffReportForSubmissionWithTemplate(exerciseId, submissionId).subscribe((resp) => expect(resp).toEqual(expected));
        const url = `${resourceUrl}/${exerciseId}/submissions/${submissionId}/diff-report-with-template`;
        const req = httpMock.expectOne({ method: 'GET', url });
        req.flush(expected);
        tick();
    }));

    it('should make GET request to retrieve diff between submissions', fakeAsync(() => {
        const exerciseId = 1;
        const submissionId = 2;
        const submissionId2 = 3;
        const expected = { id: 1, entries: [new ProgrammingExerciseGitDiffEntry()] } as unknown as ProgrammingExerciseGitDiffReport;
        service.getDiffReportForSubmissions(exerciseId, submissionId, submissionId2).subscribe((resp) => expect(resp).toEqual(expected));
        const url = `${resourceUrl}/${exerciseId}/submissions/${submissionId}/diff-report/${submissionId2}`;
        const req = httpMock.expectOne({ method: 'GET', url });
        req.flush(expected);
        tick();
    }));

    it('should make GET request to retrieve diff between commits for CommitDetailsView', fakeAsync(() => {
        const exerciseId = 1;
        const participationId = 2;
        const commitId = '2';
        const commitId2 = '3';
        const repositoryType = 'TEMPLATE';
        const expected = { id: 1, entries: [new ProgrammingExerciseGitDiffEntry()] } as unknown as ProgrammingExerciseGitDiffReport;
        service.getDiffReportForCommits(exerciseId, participationId, commitId, commitId2, repositoryType).subscribe((resp) => expect(resp).toEqual(expected));
        const url = `${resourceUrl}/${exerciseId}/commits/${commitId}/diff-report/${commitId2}?repositoryType=${repositoryType}&participationId=${participationId}`;
        const req = httpMock.expectOne({ method: 'GET', url });
        req.flush(expected);
        tick();
    }));

    it('should generate Structure Oracle', fakeAsync(() => {
        const exerciseId = 1;
        const expectedResult = 'oracle-structure';
        service.generateStructureOracle(exerciseId).subscribe((structure) => expect(structure).toEqual(expectedResult));
        const url = `${resourceUrl}/${exerciseId}/generate-tests`;
        const req = httpMock.expectOne({ method: 'PUT', url });
        req.flush(expectedResult);
        tick();
    }));

    it('should reset Exercise', fakeAsync(() => {
        const exerciseId = 1;
        const options = {
            deleteBuildPlans: true,
            deleteRepositories: true,
            deleteParticipationsSubmissionsAndResults: true,
            recreateBuildPlans: true,
        };
        const expectedResult = 'success';
        service.reset(exerciseId, options).subscribe((response) => expect(response).toEqual(expectedResult));
        const url = `${resourceUrl}/${exerciseId}/reset`;
        const req = httpMock.expectOne({ method: 'PUT', url });
        req.flush(expectedResult);
        tick();
    }));

    it('should combine Template Repository Commits', () => {
        const exerciseId = 1;
        service.combineTemplateRepositoryCommits(exerciseId).subscribe();
        const url = `${resourceUrl}/${exerciseId}/combine-template-commits`;
        const req = httpMock.expectOne({ method: 'PUT', url });
        req.flush({ body: 'something' });
    });

    it('export instructor repository', fakeAsync(() => {
        const exerciseId = 1;
        service.exportInstructorRepository(exerciseId, RepositoryType.AUXILIARY, undefined).subscribe();
        const url = `${resourceUrl}/${exerciseId}/export-instructor-repository/AUXILIARY`;
        const req = httpMock.expectOne({ method: 'GET', url });
        req.flush(new Blob());
        tick();
    }));

    it('should export Student Requested Repository', fakeAsync(() => {
        const exerciseId = 1;
        service.exportStudentRequestedRepository(exerciseId, true).subscribe();
        const url = `${resourceUrl}/${exerciseId}/export-student-requested-repository?includeTests=true`;
        const req = httpMock.expectOne({ method: 'GET', url });
        req.flush(new Blob());
        tick();
    }));

    it('should export a student repository', fakeAsync(() => {
        const exerciseId = 1;
        const participationId = 5;
        service.exportStudentRepository(exerciseId, participationId).subscribe();
        const url = `${resourceUrl}/${exerciseId}/export-student-repository/${participationId}`;
        const req = httpMock.expectOne({ method: 'GET', url });
        req.flush(new Blob());
        tick();
    }));

    it('should check plagiarism report', fakeAsync(() => {
        const exerciseId = 1;
        service.checkPlagiarismJPlagReport(exerciseId).subscribe();
        const url = `${resourceUrl}/${exerciseId}/check-plagiarism-jplag-report`;
        const req = httpMock.expectOne({ method: 'GET', url });
        req.flush(new Blob());
        tick();
    }));

    it.each([
        { uri: 'template-files-content', method: 'getTemplateRepositoryTestFilesWithContent' },
        { uri: 'solution-files-content', method: 'getSolutionRepositoryTestFilesWithContent' },
        { uri: 'with-participations', method: 'findWithTemplateAndSolutionParticipationAndResults' },
        { uri: 'check-plagiarism', method: 'checkPlagiarism' },
        { uri: 'plagiarism-result', method: 'getLatestPlagiarismResult' },
        { uri: 'test-case-state', method: 'getProgrammingExerciseTestCaseState' },
        { uri: 'diff-report', method: 'getDiffReport' },
    ])('should call correct exercise endpoint', (test) =>
        fakeAsync(() => {
            const exerciseId = 1;
            const functionToCall = service[test.method as keyof ProgrammingExerciseService];
            if (typeof functionToCall !== 'function') {
                throw new Error(`Method ${test.method} does not exist on service`);
            }
            functionToCall.bind(service, exerciseId).apply().subscribe();
            const url = `${resourceUrl}/${exerciseId}/${test.uri}`;

            // Custom matcher function
            const urlMatcher = (reqUrl: string) => reqUrl.startsWith(url);

            const req = httpMock.expectOne((request) => {
                return request.method === 'GET' && urlMatcher(request.url);
            });

            req.flush({});
            tick();
        })(),
    );

    afterEach(() => {
        httpMock.verify();
    });
});
