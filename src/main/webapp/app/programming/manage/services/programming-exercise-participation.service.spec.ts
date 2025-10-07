import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of } from 'rxjs';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { provideHttpClient } from '@angular/common/http';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { EntityTitleService } from 'app/core/navbar/entity-title.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

describe('ProgrammingExerciseParticipation Service', () => {
    let service: ProgrammingExerciseParticipationService;
    let httpMock: HttpTestingController;
    let accountService: AccountService;
    let entityTitleService: EntityTitleService;
    const resourceUrlParticipations = 'api/programming/programming-exercise-participations/';
    const resourceUrl = 'api/programming/programming-exercise/';

    let titleSpy: jest.SpyInstance;
    let accessRightsSpy: jest.SpyInstance;
    let entityTitleSpy: jest.SpyInstance;
    let setTitleSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ProgrammingExerciseParticipationService);
                httpMock = TestBed.inject(HttpTestingController);
                accountService = TestBed.inject(AccountService);
                entityTitleService = TestBed.inject(EntityTitleService);

                titleSpy = jest.spyOn(service, 'sendTitlesToEntityTitleService');
                accessRightsSpy = jest.spyOn(accountService, 'setAccessRightsForExerciseAndReferencedCourse');
                entityTitleSpy = jest.spyOn(entityTitleService, 'setExerciseTitle');
                setTitleSpy = jest.spyOn(entityTitleService, 'setTitle');
            });
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    describe('REST calls', () => {
        it('getLatestResultWithFeedback', fakeAsync(() => {
            const participation = { id: 21, exercise: { id: 321 } };
            const result = { id: 42, rated: true, submission: { id: 43, participation } } as Result;
            const expected = Object.assign({}, result);

            service.getLatestResultWithFeedback(participation.id).subscribe((resp) => expect(resp).toEqual(expected));

            const expectedURL = `${resourceUrlParticipations}${participation.id}/latest-result-with-feedbacks?withSubmission=true`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(result);
            tick();
            expect(titleSpy).toHaveBeenCalledExactlyOnceWith(participation);
            expect(accessRightsSpy).toHaveBeenCalledExactlyOnceWith(participation.exercise);
        }));

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
            const expectedURL = `${resourceUrl}${exerciseId}/files-content-commit-details/${commitId}?repositoryType=${repositoryType}&participationId=${participationId}`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(files);
            tick();
        }));
    });

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

    it('should make GET request to retrieve vcs access log for participation', fakeAsync(() => {
        const participationId = 42;
        service.getVcsAccessLogForParticipation(participationId).subscribe();
        const expectedURL = `${resourceUrlParticipations}${participationId}/vcs-access-log`;
        httpMock.expectOne({ method: 'GET', url: expectedURL });
        tick();
    }));

    it('should make GET request to retrieve vcs access log for the template repository', fakeAsync(() => {
        const exerciseId = 42;
        const repositoryType = 'TEMPLATE';
        service.getVcsAccessLogForRepository(exerciseId, repositoryType).subscribe();
        const expectedURL = `${resourceUrl}${exerciseId}/vcs-access-log/${repositoryType}`;
        httpMock.expectOne({ method: 'GET', url: expectedURL });
        tick();
    }));

    it('should make GET request to retrieve commit history for auxiliary repository', fakeAsync(() => {
        const exerciseId = 42;
        const auxiliaryRepositoryId = 123;
        const commitHistory = [{ hash: '123', author: 'author', timestamp: dayjs('2021-01-01'), message: 'commit message' }];

        service.retrieveCommitHistoryForAuxiliaryRepository(exerciseId, auxiliaryRepositoryId).subscribe((resp) => {
            expect(resp).toEqual(commitHistory);
        });

        const expectedURL = `${resourceUrl}${exerciseId}/commit-history/AUXILIARY?repositoryId=${auxiliaryRepositoryId}`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(commitHistory);
        tick();
    }));

    it('should handle getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView without repositoryType', fakeAsync(() => {
        const participationId = 42;
        const exerciseId = 123;
        const commitId = 'commitId';
        const files = new Map<string, string>();
        files.set('file1', 'content1');

        service.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(exerciseId, participationId, commitId).subscribe();

        const expectedURL = `${resourceUrl}${exerciseId}/files-content-commit-details/${commitId}?participationId=${participationId}`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(files);
        tick();
    }));

    it('should handle getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView without participationId', fakeAsync(() => {
        const exerciseId = 123;
        const commitId = 'commitId';
        const repositoryType = 'SOLUTION';
        const files = new Map<string, string>();
        files.set('file1', 'content1');

        service.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(exerciseId, undefined, commitId, repositoryType).subscribe();

        const expectedURL = `${resourceUrl}${exerciseId}/files-content-commit-details/${commitId}?repositoryType=${repositoryType}`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(files);
        tick();
    }));

    it('should handle getLatestResultWithFeedback when result is undefined', fakeAsync(() => {
        const participationId = 21;

        service.getLatestResultWithFeedback(participationId, false).subscribe((resp) => expect(resp).toBeNull());

        const expectedURL = `${resourceUrlParticipations}${participationId}/latest-result-with-feedbacks?withSubmission=false`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(null);
        tick();
        expect(titleSpy).not.toHaveBeenCalled();
        expect(accessRightsSpy).not.toHaveBeenCalled();
    }));

    describe('sendTitlesToEntityTitleService', () => {
        it('should set exercise and course titles when participation has exercise and course', () => {
            const participation: Participation = {
                id: 1,
                exercise: {
                    id: 2,
                    title: 'Test Exercise',
                    course: {
                        id: 3,
                        title: 'Test Course',
                    },
                } as any,
            };

            service.sendTitlesToEntityTitleService(participation);

            expect(entityTitleSpy).toHaveBeenCalledWith(participation.exercise);
            expect(setTitleSpy).toHaveBeenCalledWith('COURSE', [3], 'Test Course');
        });

        it('should set exercise title when participation has exercise but no course', () => {
            const participation: Participation = {
                id: 1,
                exercise: {
                    id: 2,
                    title: 'Test Exercise',
                } as any,
            };

            service.sendTitlesToEntityTitleService(participation);

            expect(entityTitleSpy).toHaveBeenCalledWith(participation.exercise);
            expect(setTitleSpy).toHaveBeenCalledWith('EXERCISE', [2], 'Test Exercise');
        });

        it('should not set any titles when participation has no exercise', () => {
            const participation: Participation = {
                id: 1,
            };

            service.sendTitlesToEntityTitleService(participation);

            expect(entityTitleSpy).not.toHaveBeenCalled();
            expect(setTitleSpy).not.toHaveBeenCalled();
        });

        it('should not set any titles when participation is undefined', () => {
            service.sendTitlesToEntityTitleService(undefined);

            expect(entityTitleSpy).not.toHaveBeenCalled();
            expect(setTitleSpy).not.toHaveBeenCalled();
        });
    });

    describe('edge cases', () => {
        it('should handle empty response for getVcsAccessLogForParticipation', fakeAsync(() => {
            const participationId = 42;

            service.getVcsAccessLogForParticipation(participationId).subscribe((resp) => {
                expect(resp).toBeUndefined();
            });

            const expectedURL = `${resourceUrlParticipations}${participationId}/vcs-access-log`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(null, { status: 200, statusText: 'OK' });
            tick();
        }));

        it('should handle empty response for getVcsAccessLogForRepository', fakeAsync(() => {
            const exerciseId = 42;
            const repositoryType = 'TEMPLATE';

            service.getVcsAccessLogForRepository(exerciseId, repositoryType).subscribe((resp) => {
                expect(resp).toBeUndefined();
            });

            const expectedURL = `${resourceUrl}${exerciseId}/vcs-access-log/${repositoryType}`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(null, { status: 200, statusText: 'OK' });
            tick();
        }));

        it('should handle null response for getParticipationRepositoryFilesWithContentAtCommit', fakeAsync(() => {
            const participationId = 42;
            const commitId = 'commitId';

            service.getParticipationRepositoryFilesWithContentAtCommit(participationId, commitId).subscribe((resp) => {
                expect(resp).toBeNull();
            });

            const expectedURL = `${resourceUrlParticipations}${participationId}/files-content/${commitId}`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(null);
            tick();
        }));
    });
});
