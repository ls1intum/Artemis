import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
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
    setupTestBed({ zoneless: true });

    let service: ProgrammingExerciseParticipationService;
    let httpMock: HttpTestingController;
    let accountService: AccountService;
    let entityTitleService: EntityTitleService;
    const resourceUrlParticipations = 'api/programming/programming-exercise-participations/';
    const resourceUrl = 'api/programming/programming-exercises/';

    let titleSpy: ReturnType<typeof vi.spyOn>;
    let accessRightsSpy: ReturnType<typeof vi.spyOn>;
    let entityTitleSpy: ReturnType<typeof vi.spyOn>;
    let setTitleSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        }).compileComponents();

        service = TestBed.inject(ProgrammingExerciseParticipationService);
        httpMock = TestBed.inject(HttpTestingController);
        accountService = TestBed.inject(AccountService);
        entityTitleService = TestBed.inject(EntityTitleService);

        titleSpy = vi.spyOn(service, 'sendTitlesToEntityTitleService');
        accessRightsSpy = vi.spyOn(accountService, 'setAccessRightsForExerciseAndReferencedCourse');
        entityTitleSpy = vi.spyOn(entityTitleService, 'setExerciseTitle');
        setTitleSpy = vi.spyOn(entityTitleService, 'setTitle');
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('REST calls', () => {
        it('getLatestResultWithFeedback', () => {
            const participation = { id: 21, exercise: { id: 321 } };
            const result = { id: 42, rated: true, submission: { id: 43, participation } } as Result;
            const expected = Object.assign({}, result);

            service.getLatestResultWithFeedback(participation.id).subscribe((resp) => expect(resp).toEqual(expected));

            const expectedURL = `${resourceUrlParticipations}${participation.id}/latest-result-with-feedbacks?withSubmission=true`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(result);
            expect(titleSpy).toHaveBeenCalledExactlyOnceWith(participation);
            expect(accessRightsSpy).toHaveBeenCalledExactlyOnceWith(participation.exercise);
        });

        it('getStudentParticipationWithLatestResult', () => {
            const participation = { id: 42, exercise: { id: 123 } };
            const expected = Object.assign({}, participation);

            service.getStudentParticipationWithLatestResult(participation.id).subscribe((resp) => expect(resp).toEqual(expected));

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrlParticipations}${participation.id}/student-participation-with-latest-result-and-feedbacks` });
            req.flush(participation);
            expect(titleSpy).toHaveBeenCalledExactlyOnceWith(participation);
            expect(accessRightsSpy).toHaveBeenCalledExactlyOnceWith(participation.exercise);
        });

        it('getStudentParticipationWithAllResults', () => {
            const participation = { id: 42, exercise: { id: 123 } };
            const expected = Object.assign({}, participation);

            service.getStudentParticipationWithAllResults(participation.id).subscribe((resp) => expect(resp).toEqual(expected));

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrlParticipations}${participation.id}/student-participation-with-all-results` });
            req.flush(participation);
            expect(titleSpy).toHaveBeenCalledExactlyOnceWith(participation);
            expect(accessRightsSpy).toHaveBeenCalledExactlyOnceWith(participation.exercise);
        });

        it('checkIfParticipationHasResult', () => {
            const participation = { id: 42 };

            service.checkIfParticipationHasResult(participation.id).subscribe((resp) => expect(resp).toBe(true));

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrlParticipations}${participation.id}/has-result` });
            req.flush(true);
        });

        it.each([
            { participationId: 42, gradedParticipationId: 21 },
            { participationId: 42, gradedParticipationId: undefined },
        ])('resetRepository', ({ participationId, gradedParticipationId }: { participationId: number; gradedParticipationId?: number }) => {
            let successful = false;
            service.resetRepository(participationId, gradedParticipationId).subscribe(() => (successful = true));

            const expectedURL =
                `${resourceUrlParticipations}${participationId}/reset-repository` + (gradedParticipationId ? `?gradedParticipationId=${gradedParticipationId}` : '');
            const req = httpMock.expectOne({ method: 'PUT', url: expectedURL });
            req.flush(of({}));
            expect(successful).toBe(true);
        });

        it('retrieveCommitHistoryForParticipation', () => {
            const participationId = 42;
            const commitHistory = [{ hash: '123', author: 'author', timestamp: dayjs('2021-01-01'), message: 'commit message' }];
            service.retrieveCommitHistoryForParticipation(participationId).subscribe((resp) => {
                expect(resp).toEqual(commitHistory);
            });

            const expectedURL = `${resourceUrlParticipations}${participationId}/commit-history`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(commitHistory);
        });

        it('retrieveCommitHistoryForTemplateSolutionOrTests', () => {
            const participationId = 42;
            const repositoryType = 'SOLUTION';
            const commitHistory = [{ hash: '123', author: 'author', timestamp: dayjs('2021-01-01'), message: 'commit message' }];
            service.retrieveCommitHistoryForTemplateSolutionOrTests(participationId, repositoryType).subscribe((resp) => {
                expect(resp).toEqual(commitHistory);
            });

            const expectedURL = `${resourceUrl}${participationId}/commit-history/${repositoryType}`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(commitHistory);
        });

        it('getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView', () => {
            const participationId = 42;
            const exerciseId = 123;
            const commitId = 'commitId';
            const repositoryType = 'SOLUTION';
            const files = new Map<string, string>();
            files.set('file1', 'content1');
            files.set('file2', 'content2');
            service.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(exerciseId, participationId, commitId, repositoryType).subscribe();
            const expectedURL = `${resourceUrl}${exerciseId}/files-content-commit-details?commitId=${commitId}&repositoryType=${repositoryType}&participationId=${participationId}`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(files);
        });
    });

    it('should make GET request to retrieve files with content at commit', () => {
        const participationId = 42;
        const commitId = 'commitId';
        const files = new Map<string, string>();
        files.set('file1', 'content1');
        files.set('file2', 'content2');
        service.getParticipationRepositoryFilesWithContentAtCommit(participationId, commitId).subscribe();
        const expectedURL = `${resourceUrlParticipations}${participationId}/files-content?commitId=${commitId}`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(files);
    });

    it('should make GET request to retrieve vcs access log for participation', () => {
        const participationId = 42;
        service.getVcsAccessLogForParticipation(participationId).subscribe();
        const expectedURL = `${resourceUrlParticipations}${participationId}/vcs-access-log`;
        httpMock.expectOne({ method: 'GET', url: expectedURL });
    });

    it('should make GET request to retrieve vcs access log for the template repository', () => {
        const exerciseId = 42;
        const repositoryType = 'TEMPLATE';
        service.getVcsAccessLogForRepository(exerciseId, repositoryType).subscribe();
        const expectedURL = `${resourceUrl}${exerciseId}/vcs-access-log/${repositoryType}`;
        httpMock.expectOne({ method: 'GET', url: expectedURL });
    });

    it('should make GET request to retrieve commit history for auxiliary repository', () => {
        const exerciseId = 42;
        const auxiliaryRepositoryId = 123;
        const commitHistory = [{ hash: '123', author: 'author', timestamp: dayjs('2021-01-01'), message: 'commit message' }];

        service.retrieveCommitHistoryForAuxiliaryRepository(exerciseId, auxiliaryRepositoryId).subscribe((resp) => {
            expect(resp).toEqual(commitHistory);
        });

        const expectedURL = `${resourceUrl}${exerciseId}/commit-history/AUXILIARY?repositoryId=${auxiliaryRepositoryId}`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(commitHistory);
    });

    it('should handle getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView without repositoryType', () => {
        const participationId = 42;
        const exerciseId = 123;
        const commitId = 'commitId';
        const files = new Map<string, string>();
        files.set('file1', 'content1');

        service.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(exerciseId, participationId, commitId).subscribe();

        const expectedURL = `${resourceUrl}${exerciseId}/files-content-commit-details?commitId=${commitId}&participationId=${participationId}`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(files);
    });

    it('should handle getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView without participationId', () => {
        const exerciseId = 123;
        const commitId = 'commitId';
        const repositoryType = 'SOLUTION';
        const files = new Map<string, string>();
        files.set('file1', 'content1');

        service.getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView(exerciseId, undefined, commitId, repositoryType).subscribe();

        const expectedURL = `${resourceUrl}${exerciseId}/files-content-commit-details?commitId=${commitId}&repositoryType=${repositoryType}`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(files);
    });

    it('should handle getLatestResultWithFeedback when result is undefined', () => {
        const participationId = 21;

        service.getLatestResultWithFeedback(participationId, false).subscribe((resp) => expect(resp).toBeNull());

        const expectedURL = `${resourceUrlParticipations}${participationId}/latest-result-with-feedbacks?withSubmission=false`;
        const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
        req.flush(null);
        expect(titleSpy).not.toHaveBeenCalled();
        expect(accessRightsSpy).not.toHaveBeenCalled();
    });

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
        it('should handle empty response for getVcsAccessLogForParticipation', () => {
            const participationId = 42;

            service.getVcsAccessLogForParticipation(participationId).subscribe((resp) => {
                expect(resp).toBeUndefined();
            });

            const expectedURL = `${resourceUrlParticipations}${participationId}/vcs-access-log`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(null, { status: 200, statusText: 'OK' });
        });

        it('should handle empty response for getVcsAccessLogForRepository', () => {
            const exerciseId = 42;
            const repositoryType = 'TEMPLATE';

            service.getVcsAccessLogForRepository(exerciseId, repositoryType).subscribe((resp) => {
                expect(resp).toBeUndefined();
            });

            const expectedURL = `${resourceUrl}${exerciseId}/vcs-access-log/${repositoryType}`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(null, { status: 200, statusText: 'OK' });
        });

        it('should handle null response for getParticipationRepositoryFilesWithContentAtCommit', () => {
            const participationId = 42;
            const commitId = 'commitId';

            service.getParticipationRepositoryFilesWithContentAtCommit(participationId, commitId).subscribe((resp) => {
                expect(resp).toBeNull();
            });

            const expectedURL = `${resourceUrlParticipations}${participationId}/files-content?commitId=${commitId}`;
            const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
            req.flush(null);
        });
    });
});
