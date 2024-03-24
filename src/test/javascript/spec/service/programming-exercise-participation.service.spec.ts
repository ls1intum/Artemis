import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of } from 'rxjs';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { Submission } from 'app/entities/submission.model';
import { ArtemisTestModule } from '../test.module';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';

describe('ProgrammingExerciseParticipation Service', () => {
    let service: ProgrammingExerciseParticipationService;
    let httpMock: HttpTestingController;
    let accountService: AccountService;
    const resourceUrlParticipations = 'api/programming-exercise-participations/';
    const resourceUrl = 'api/programming-exercise/';

    let titleSpy: jest.SpyInstance;
    let accessRightsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ProgrammingExerciseParticipationService);
                httpMock = TestBed.inject(HttpTestingController);
                accountService = TestBed.inject(AccountService);

                titleSpy = jest.spyOn(service, 'sendTitlesToEntityTitleService');
                accessRightsSpy = jest.spyOn(accountService, 'setAccessRightsForExerciseAndReferencedCourse');
            });
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    describe('REST calls', () => {
        it.each([true, false])(
            'getLatestResultWithFeedback',
            fakeAsync((withSubmission: boolean) => {
                const participation = { id: 21, exercise: { id: 321 } };
                const result = { id: 42, rated: true, submission: withSubmission ? ({ id: 43 } as Submission) : undefined, participation } as Result;
                const expected = Object.assign({}, result);

                service.getLatestResultWithFeedback(participation.id, withSubmission).subscribe((resp) => expect(resp).toEqual(expected));

                const expectedURL = `${resourceUrlParticipations}${participation.id}/latest-result-with-feedbacks?withSubmission=${withSubmission}`;
                const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
                req.flush(result);
                tick();
                expect(titleSpy).toHaveBeenCalledExactlyOnceWith(participation);
                expect(accessRightsSpy).toHaveBeenCalledExactlyOnceWith(participation.exercise);
            }),
        );

        it('getStudentParticipationWithLatestResult', fakeAsync(() => {
            const participation = { id: 42, exercise: { id: 123 } };
            const expected = Object.assign({}, participation);

            service.getStudentParticipationWithLatestResult(participation.id).subscribe((resp) => expect(resp).toEqual(expected));

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrlParticipations}${participation.id}/student-participation-with-latest-result-and-feedbacks` });
            req.flush(participation);
            tick();
            expect(titleSpy).toHaveBeenCalledExactlyOnceWith(participation);
            expect(accessRightsSpy).toHaveBeenCalledExactlyOnceWith(participation.exercise);
        }));

        it('getStudentParticipationWithAllResults', fakeAsync(() => {
            const participation = { id: 42, exercise: { id: 123 } };
            const expected = Object.assign({}, participation);

            service.getStudentParticipationWithAllResults(participation.id).subscribe((resp) => expect(resp).toEqual(expected));

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrlParticipations}${participation.id}/student-participation-with-all-results` });
            req.flush(participation);
            tick();
            expect(titleSpy).toHaveBeenCalledExactlyOnceWith(participation);
            expect(accessRightsSpy).toHaveBeenCalledExactlyOnceWith(participation.exercise);
        }));

        it('checkIfParticipationHasResult', fakeAsync(() => {
            const participation = { id: 42 };

            service.checkIfParticipationHasResult(participation.id).subscribe((resp) => expect(resp).toBeTrue());

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrlParticipations}${participation.id}/has-result` });
            req.flush(true);
            tick();
        }));

        it.each([{ participationId: 42, gradedParticipationId: 21 }, { participationId: 42 }])(
            'resetRepository',
            fakeAsync((participationId: number, gradedParticipationId?: number) => {
                let successful = false;
                service.resetRepository(participationId, gradedParticipationId).subscribe(() => (successful = true));

                const expectedURL =
                    `${resourceUrlParticipations}${participationId}/reset-repository` + (gradedParticipationId ? `?gradedParrticipationId=${gradedParticipationId}` : '');
                const req = httpMock.expectOne({ method: 'PUT', url: expectedURL });
                req.flush(of({}));
                tick();
                expect(successful).toBeTrue();
            }),
        );

        it('retrieveCommitHistoryForParticipation', fakeAsync(() => {
            const participationId = 42;
            const commitHistory = [{ hash: '123', author: 'author', timestamp: dayjs('2021-01-01'), message: 'commit message' }];
            service.retrieveCommitHistoryForParticipation(participationId).subscribe((resp) => {
                expect(resp).toEqual(commitHistory);
            });

            const expectedURL = `${resourceUrlParticipations}${participationId}/commit-history`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(commitHistory);
            tick();
        }));

        it('retrieveCommitHistoryForTemplateSolutionOrTests', fakeAsync(() => {
            const participationId = 42;
            const repositoryType = 'SOLUTION';
            const commitHistory = [{ hash: '123', author: 'author', timestamp: dayjs('2021-01-01'), message: 'commit message' }];
            service.retrieveCommitHistoryForTemplateSolutionOrTests(participationId, repositoryType).subscribe((resp) => {
                expect(resp).toEqual(commitHistory);
            });

            const expectedURL = `${resourceUrl}${participationId}/commit-history/${repositoryType}`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(commitHistory);
            tick();
        }));

        it('getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView', fakeAsync(() => {
            const participationId = 42;
            const exerciseId = 123;
            const commitId = 'commitId';
            const repositoryType = 'SOLUTION';
            const files = new Map<string, string>();
            files.set('file1', 'content1');
            files.set('file2', 'content2');
            service.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(exerciseId, participationId, commitId, repositoryType).subscribe();
            const expectedURL = `${resourceUrl}${exerciseId}/participation/${participationId}/files-content-commit-details/${commitId}?repositoryType=${repositoryType}`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(files);
            tick();
        }));
    });

    it('should make GET request to retrieve commits infos for participation', fakeAsync(() => {
        const participationId = 42;
        const commitInfos = [{ hash: '123', author: 'author', timestamp: dayjs('2021-01-01'), message: 'commit message' }];
        service.retrieveCommitsInfoForParticipation(participationId).subscribe((resp) => {
            expect(resp).toEqual(commitInfos);
        });

        const expectedURL = `${resourceUrlParticipations}${participationId}/commits-info`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(commitInfos);
        tick();
    }));

    it('should make GET request to retrieve files with content at commit', fakeAsync(() => {
        const participationId = 42;
        const commitId = 'commitId';
        const files = new Map<string, string>();
        files.set('file1', 'content1');
        files.set('file2', 'content2');
        service.getParticipationRepositoryFilesWithContentAtCommit(participationId, commitId).subscribe();
        const expectedURL = `${resourceUrlParticipations}${participationId}/files-content/${commitId}`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(files);
        tick();
    }));
});
