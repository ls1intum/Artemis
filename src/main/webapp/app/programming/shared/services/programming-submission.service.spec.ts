import dayjs from 'dayjs/esm';
import { BehaviorSubject, Subject, lastValueFrom, of } from 'rxjs';
import { range as _range } from 'lodash-es';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import {
    BuildTimingInfo,
    ExerciseSubmissionState,
    ProgrammingSubmissionService,
    ProgrammingSubmissionState,
    ProgrammingSubmissionStateObj,
} from 'app/programming/shared/services/programming-submission.service';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import { TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { SubmissionProcessingDTO } from 'app/programming/shared/entities/submission-processing-dto';

describe('ProgrammingSubmissionService', () => {
    let websocketService: WebsocketService;
    let httpService: HttpClient;
    let participationWebsocketService: ParticipationWebsocketService;
    let participationService: ProgrammingExerciseParticipationService;
    let submissionService: ProgrammingSubmissionService;

    let httpMock: HttpTestingController;
    let httpGetStub: jest.SpyInstance;
    let wsSubscribeStub: jest.SpyInstance;
    let wsUnsubscribeStub: jest.SpyInstance;
    let wsReceiveStub: jest.SpyInstance;
    let participationWsLatestResultStub: jest.SpyInstance;
    let getLatestResultStub: jest.SpyInstance;
    let notifyAllResultSubscribersStub: jest.SpyInstance;

    let wsSubmissionSubject: Subject<Submission | undefined>;
    let wsSubmissionProcessingSubject: Subject<SubmissionProcessingDTO | undefined>;
    let wsLatestResultSubject: Subject<Result | undefined>;

    const participationId = 1;
    const exerciseId = 10;
    const submissionTopic = `/user/topic/newSubmissions`;
    const submissionProcessingTopic = `/user/topic/submissionProcessing`;
    let currentSubmission: Submission;
    let currentSubmission2: Submission;
    let currentProgrammingSubmission: ProgrammingSubmission;
    let currentProgrammingSubmissionOld: ProgrammingSubmission;
    let result: Result;
    let result2: Result;
    let buildTimingInfoEmpty: BuildTimingInfo;
    let buildTimingInfo: BuildTimingInfo;
    let mockSubmissionProcessingDTO: SubmissionProcessingDTO;
    let mockSubmissionProcessingDTOOld: SubmissionProcessingDTO;

    beforeEach(() => {
        currentSubmission = { id: 11, submissionDate: dayjs().subtract(20, 'seconds'), participation: { id: participationId } } as any;
        currentSubmission2 = { id: 12, submissionDate: dayjs().subtract(20, 'seconds'), participation: { id: participationId } } as any;
        result = { id: 31, submission: currentSubmission } as any;
        result2 = { id: 32, submission: currentSubmission2 } as any;
        buildTimingInfoEmpty = { buildStartDate: undefined, estimatedCompletionDate: undefined };
        buildTimingInfo = { buildStartDate: dayjs().subtract(10, 'seconds'), estimatedCompletionDate: dayjs().add(10, 'seconds') };
        currentProgrammingSubmission = { id: 12, submissionDate: dayjs().subtract(20, 'seconds'), participation: { id: participationId }, commitHash: 'abc123' } as any;
        currentProgrammingSubmissionOld = { id: 11, submissionDate: dayjs().subtract(40, 'seconds'), participation: { id: participationId }, commitHash: 'abc123Old' } as any;
        mockSubmissionProcessingDTO = {
            exerciseId: exerciseId,
            participationId: participationId,
            commitHash: 'abc123',
            estimatedCompletionDate: buildTimingInfo.estimatedCompletionDate,
            buildStartDate: buildTimingInfo.buildStartDate,
            submissionDate: dayjs().subtract(20, 'seconds'),
        };
        mockSubmissionProcessingDTOOld = Object.assign({}, mockSubmissionProcessingDTO, { commitHash: 'abc123Old', submissionDate: dayjs().subtract(40, 'seconds') });

        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .compileComponents()
            .then(() => {
                submissionService = TestBed.inject(ProgrammingSubmissionService);
                websocketService = TestBed.inject(WebsocketService);
                httpService = TestBed.inject(HttpClient);
                participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
                participationService = TestBed.inject(ProgrammingExerciseParticipationService);

                httpMock = TestBed.inject(HttpTestingController);
                httpGetStub = jest.spyOn(httpService, 'get');
                wsSubscribeStub = jest.spyOn(websocketService, 'subscribe');
                wsUnsubscribeStub = jest.spyOn(websocketService, 'unsubscribe');
                wsSubmissionSubject = new Subject<Submission | undefined>();
                wsSubmissionProcessingSubject = new Subject<SubmissionProcessingDTO | undefined>();
                wsReceiveStub = jest.spyOn(websocketService, 'receive').mockImplementation((topic: string) => {
                    if (topic === submissionTopic) {
                        return wsSubmissionSubject;
                    } else if (topic === submissionProcessingTopic) {
                        return wsSubmissionProcessingSubject;
                    }
                    return new Subject();
                });
                wsLatestResultSubject = new Subject<Result | undefined>();
                participationWsLatestResultStub = jest
                    .spyOn(participationWebsocketService, 'subscribeForLatestResultOfParticipation')
                    .mockReturnValue(wsLatestResultSubject as any);
                getLatestResultStub = jest.spyOn(participationService, 'getLatestResultWithFeedback');
                notifyAllResultSubscribersStub = jest.spyOn(participationWebsocketService, 'notifyAllResultSubscribers');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should return cached subject as Observable for provided participation if exists', () => {
        const cachedSubject = new BehaviorSubject(undefined);
        // @ts-ignore
        const fetchLatestPendingSubmissionSpy = jest.spyOn(submissionService, 'fetchLatestPendingSubmissionByParticipationId');
        // @ts-ignore
        const setupWebsocketSubscriptionSpy = jest.spyOn(submissionService, 'setupWebsocketSubscriptionForLatestPendingSubmission');
        // @ts-ignore
        const subscribeForNewResultSpy = jest.spyOn(submissionService, 'subscribeForNewResult');
        // @ts-ignore
        submissionService.submissionSubjects = { [participationId]: cachedSubject };

        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true);
        expect(fetchLatestPendingSubmissionSpy).not.toHaveBeenCalled();
        expect(setupWebsocketSubscriptionSpy).not.toHaveBeenCalled();
        expect(subscribeForNewResultSpy).not.toHaveBeenCalled();
    });

    it('should query httpService endpoint and setup the websocket subscriptions if no subject is cached for the provided participation', () => {
        httpGetStub.mockReturnValue(of(currentSubmission));
        let submission;
        submissionService.isLocalCIEnabled = false;
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((sub) => (submission = sub));
        expect(submission).toEqual({
            submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION,
            submission: currentSubmission,
            participationId,
            buildTimingInfo: buildTimingInfoEmpty,
        });
        expect(wsSubscribeStub).toHaveBeenCalledOnce();
        expect(wsSubscribeStub).toHaveBeenCalledWith(submissionTopic);
        expect(wsReceiveStub).toHaveBeenCalledOnce();
        expect(wsReceiveStub).toHaveBeenCalledWith(submissionTopic);
        expect(participationWsLatestResultStub).toHaveBeenCalledOnce();
        expect(participationWsLatestResultStub).toHaveBeenCalledWith(participationId, true, 10);
    });

    it('should query httpService endpoint and setup the websocket subscriptions if no subject is cached for the provided participation with localCI profile', () => {
        httpGetStub.mockReturnValue(of(currentSubmission));
        let submission;
        submissionService.isLocalCIEnabled = true;
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((sub) => (submission = sub));
        expect(submission).toEqual({
            submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION,
            submission: currentSubmission,
            participationId,
            buildTimingInfo: buildTimingInfoEmpty,
        });
        expect(wsSubscribeStub).toHaveBeenCalledTimes(2);
        expect(wsSubscribeStub).toHaveBeenNthCalledWith(1, submissionTopic);
        expect(wsSubscribeStub).toHaveBeenNthCalledWith(2, submissionProcessingTopic);
        expect(wsReceiveStub).toHaveBeenCalledTimes(2);
        expect(wsReceiveStub).toHaveBeenNthCalledWith(1, submissionTopic);
        expect(wsReceiveStub).toHaveBeenNthCalledWith(2, submissionProcessingTopic);
        expect(participationWsLatestResultStub).toHaveBeenCalledOnce();
        expect(participationWsLatestResultStub).toHaveBeenCalledWith(participationId, true, 10);
    });

    it('should emit undefined when a new result comes in for the given participation to signal that the building process is over', () => {
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        httpGetStub.mockReturnValue(of(currentSubmission));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId, buildTimingInfo: buildTimingInfoEmpty },
        ]);
        // Result comes in for submission.
        result.submission = currentSubmission;
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId, buildTimingInfo: buildTimingInfoEmpty },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    });

    it('should NOT emit undefined when a new result comes that does not belong to the currentSubmission', () => {
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        httpGetStub.mockReturnValue(of(currentSubmission));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId, buildTimingInfo: buildTimingInfoEmpty },
        ]);
        // Result comes in for submission.
        result.submission = currentSubmission2;
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId, buildTimingInfo: buildTimingInfoEmpty },
        ]);
    });

    it('should emit the newest submission when it was received through the websocket connection', () => {
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        // No latest pending submission found.
        httpGetStub.mockReturnValue(of(undefined));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        // New submission comes in.
        wsSubmissionSubject.next(currentSubmission2);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission2, participationId },
        ]);
        // Result comes in for submission.
        result.submission = currentSubmission2;
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission2, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    });

    it('should emit the failed submission state when the result waiting timer runs out AND accept a late result', fakeAsync(() => {
        // Set the timer to 10ms for testing purposes.
        // @ts-ignore
        submissionService.DEFAULT_EXPECTED_RESULT_ETA = 10;
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        httpGetStub.mockReturnValue(of(undefined));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        // We simulate that the latest result from the server does not belong the pending submission
        getLatestResultStub = getLatestResultStub.mockReturnValue(of(result));

        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        wsSubmissionSubject.next(currentSubmission2);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission2, participationId },
        ]);

        tick(10);

        // Expect the fallback mechanism to kick in after the timeout
        expect(getLatestResultStub).toHaveBeenCalledOnce();
        expect(getLatestResultStub).toHaveBeenCalledWith(participationId);

        // HAS_FAILED_SUBMISSION is expected as the result provided by getLatestResult does not match the pending submission
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission2, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: currentSubmission2, participationId },
        ]);

        // Now the result for the submission in - should be accepted even though technically too late!
        wsLatestResultSubject.next(result2);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission2, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: currentSubmission2, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    }));

    it('should emit has no pending submission if the fallback mechanism fetches the right result from the server', fakeAsync(() => {
        // Set the timer to 10ms for testing purposes.
        // @ts-ignore
        submissionService.DEFAULT_EXPECTED_RESULT_ETA = 10;
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        httpGetStub.mockReturnValue(of(undefined));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        // We simulate that the latest result from the server matches the pending submission
        getLatestResultStub = getLatestResultStub.mockReturnValue(of(result));

        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        wsSubmissionSubject.next(currentSubmission);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId },
        ]);

        tick(10);

        // Expect the fallback mechanism to kick in after the timeout
        expect(getLatestResultStub).toHaveBeenCalledOnce();
        expect(getLatestResultStub).toHaveBeenCalledWith(participationId);
        expect(notifyAllResultSubscribersStub).toHaveBeenCalledOnce();
        expect(notifyAllResultSubscribersStub).toHaveBeenCalledWith(Object.assign({}, result));
        wsLatestResultSubject.next(result);

        // HAS_NO_PENDING_SUBMISSION is expected as the result provided by getLatestResult matches the pending submission
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);

        // If the "real" websocket connection triggers now for some reason, the submission state should not change
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    }));

    it('should fetch the latest pending submissions for all participations of an exercise if preloadLatestPendingSubmissionsForExercise is called', () => {
        const exerciseId = 3;
        const participation1 = { id: 2 } as StudentParticipation;
        const participation2 = { id: 3 } as StudentParticipation;
        // This participation will not be cached.
        const participation3 = { id: 4 } as StudentParticipation;
        let submissionState, submission;

        const pendingSubmissions = { [participation1.id!]: currentSubmission, [participation2.id!]: currentSubmission2 };

        // @ts-ignore
        const fetchLatestPendingSubmissionSpy = jest.spyOn(submissionService, 'fetchLatestPendingSubmissionByParticipationId');

        // This load the submissions for participation 1 and 2, but not for 3.
        lastValueFrom(submissionService.getSubmissionStateOfExercise(exerciseId));
        const request = httpMock.expectOne('api/programming/programming-exercises/3/latest-pending-submissions');

        // When retrieving the status of participation 1 we should wait until a result of the exercise request is present
        submissionService.getLatestPendingSubmissionByParticipationId(participation1.id!, exerciseId, true).subscribe(({ submissionState: state, submission: sub }) => {
            submissionState = state;
            submission = sub;
        });
        expect(fetchLatestPendingSubmissionSpy).not.toHaveBeenCalled();

        request.flush(pendingSubmissions);
        expect(submissionState).toBe(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        expect(submission).toEqual(currentSubmission);

        // Fetching the latest pending submission again should not trigger a rest call for a cached submission.
        submissionService.getLatestPendingSubmissionByParticipationId(participation1.id!, exerciseId, true).subscribe(({ submissionState: state, submission: sub }) => {
            submissionState = state;
            submission = sub;
        });
        expect(fetchLatestPendingSubmissionSpy).not.toHaveBeenCalled();
        expect(submissionState).toBe(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        expect(submission).toEqual(currentSubmission);

        // Fetching the latest pending submission should trigger a rest call if the submission is not cached.
        submissionService.getLatestPendingSubmissionByParticipationId(participation3.id!, exerciseId, true).subscribe();
        expect(fetchLatestPendingSubmissionSpy).toHaveBeenCalledOnce();
        expect(fetchLatestPendingSubmissionSpy).toHaveBeenCalledWith(participation3.id);
    });

    it('should not throw an error on getSubmissionStateOfExercise if state is an empty object', () => {
        const exerciseId = 10;
        const submissionState: ExerciseSubmissionState = {};
        // @ts-ignore
        const fetchLatestPendingSubmissionsByExerciseIdSpy = jest.spyOn(submissionService, 'fetchLatestPendingSubmissionsByExerciseId');
        httpGetStub.mockReturnValue(of(submissionState));

        let receivedSubmissionState: ExerciseSubmissionState = {};
        submissionService.getSubmissionStateOfExercise(exerciseId).subscribe((state) => (receivedSubmissionState = state));
        expect(fetchLatestPendingSubmissionsByExerciseIdSpy).toHaveBeenCalledOnce();
        expect(fetchLatestPendingSubmissionsByExerciseIdSpy).toHaveBeenCalledWith(exerciseId);
        expect(receivedSubmissionState).toEqual(submissionState);
        expect(httpGetStub).toHaveBeenCalledOnce();
        expect(httpGetStub).toHaveBeenCalledWith(`api/programming/programming-exercises/${exerciseId}/latest-pending-submissions`);
    });

    it('should correctly return the submission state based on the servers response', () => {
        const exerciseId = 10;
        const submissionState = { 1: { id: 11, submissionDate: dayjs().subtract(2, 'hours') } as ProgrammingSubmission, 2: undefined };
        const expectedSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: submissionState['1'], participationId: 1 },
            2: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 2 },
        };
        // @ts-ignore
        const fetchLatestPendingSubmissionsByExerciseIdSpy = jest.spyOn(submissionService, 'fetchLatestPendingSubmissionsByExerciseId');
        httpGetStub.mockReturnValue(of(submissionState));

        let receivedSubmissionState: ExerciseSubmissionState = {};
        submissionService.getSubmissionStateOfExercise(exerciseId).subscribe((state) => (receivedSubmissionState = state));
        expect(fetchLatestPendingSubmissionsByExerciseIdSpy).toHaveBeenCalledOnce();
        expect(fetchLatestPendingSubmissionsByExerciseIdSpy).toHaveBeenCalledWith(exerciseId);
        expect(receivedSubmissionState).toEqual(expectedSubmissionState);
        expect(httpGetStub).toHaveBeenCalledOnce();
        expect(httpGetStub).toHaveBeenCalledWith(`api/programming/programming-exercises/${exerciseId}/latest-pending-submissions`);
    });

    it('should recalculate the result eta based on the number of open submissions', () => {
        const exerciseId = 10;
        // Simulate 340 participations with one pending submission each.
        const submissionState = _range(340).reduce((acc, n) => Object.assign({}, acc, { [n]: { submissionDate: dayjs().subtract(1, 'minutes') } as ProgrammingSubmission }), {});
        const expectedSubmissionState = Object.entries(submissionState).reduce(
            (acc, [participationID, submission]: [string, ProgrammingSubmission]) =>
                Object.assign({}, acc, {
                    [participationID]: { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: parseInt(participationID, 10) },
                }),
            {},
        );
        // @ts-ignore
        const fetchLatestPendingSubmissionsByExerciseIdSpy = jest.spyOn(submissionService, 'fetchLatestPendingSubmissionsByExerciseId');
        httpGetStub.mockReturnValue(of(submissionState));

        let receivedSubmissionState: ExerciseSubmissionState = {};
        submissionService.getSubmissionStateOfExercise(exerciseId).subscribe((state) => (receivedSubmissionState = state));
        expect(fetchLatestPendingSubmissionsByExerciseIdSpy).toHaveBeenCalledOnce();
        expect(fetchLatestPendingSubmissionsByExerciseIdSpy).toHaveBeenCalledWith(exerciseId);
        expect(receivedSubmissionState).toEqual(expectedSubmissionState);
        expect(httpGetStub).toHaveBeenCalledOnce();
        expect(httpGetStub).toHaveBeenCalledWith(`api/programming/programming-exercises/${exerciseId}/latest-pending-submissions`);

        let resultEta = -1;
        submissionService.getResultEtaInMs().subscribe((eta) => (resultEta = eta));

        // With 340 submissions, the eta should now have increased.
        expect(resultEta).toBe(2000 * 60 + 3 * 1000 * 60);
    });

    it('should only unsubscribe if no other participations use the topic', () => {
        httpGetStub.mockReturnValue(of(currentSubmission));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true);
        submissionService.getLatestPendingSubmissionByParticipationId(2, 10, true);

        // Should not unsubscribe as participation 2 still uses the same topic
        submissionService.unsubscribeForLatestSubmissionOfParticipation(participationId);
        expect(wsUnsubscribeStub).not.toHaveBeenCalled();

        // Should now unsubscribe as last participation for topic was unsubscribed
        submissionService.unsubscribeForLatestSubmissionOfParticipation(2);
        expect(wsUnsubscribeStub).toHaveBeenCalledOnce();
    });

    it('should only unsubscribe if no other participations use the topic with localci', () => {
        submissionService.isLocalCIEnabled = true;
        httpGetStub.mockReturnValue(of(currentSubmission));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true);
        submissionService.getLatestPendingSubmissionByParticipationId(2, 10, true);

        // Should not unsubscribe as participation 2 still uses the same topic
        submissionService.unsubscribeForLatestSubmissionOfParticipation(participationId);
        expect(wsUnsubscribeStub).not.toHaveBeenCalled();

        // Should now unsubscribe as last participation for topic was unsubscribed
        submissionService.unsubscribeForLatestSubmissionOfParticipation(2);
        expect(wsUnsubscribeStub).toHaveBeenCalledTimes(2);
    });

    it('should emit the newest submission when it was received through the websocket connection with localci', () => {
        submissionService.isLocalCIEnabled = true;
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        // No latest pending submission found.
        httpGetStub.mockReturnValue(of(undefined));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        // New submission comes in.
        wsSubmissionSubject.next(currentProgrammingSubmission);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmission, participationId },
        ]);
        // Submission is now building.
        wsSubmissionProcessingSubject.next(mockSubmissionProcessingDTO);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmission, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentProgrammingSubmission, participationId, buildTimingInfo },
        ]);
        // Result comes in for submission.
        result.submission = currentProgrammingSubmission;
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmission, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentProgrammingSubmission, participationId, buildTimingInfo },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    });

    it('should handle when submission processing event before submission event', () => {
        submissionService.isLocalCIEnabled = true;
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        // No latest pending submission found.
        httpGetStub.mockReturnValue(of(undefined));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        // Submission is now building.
        wsSubmissionProcessingSubject.next(mockSubmissionProcessingDTO);
        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        // New submission comes in.
        wsSubmissionSubject.next(currentProgrammingSubmission);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentProgrammingSubmission, participationId, buildTimingInfo },
        ]);
        // Result comes in for submission.
        result.submission = currentProgrammingSubmission;
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentProgrammingSubmission, participationId, buildTimingInfo },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    });

    it('should not update to building if old submission', () => {
        submissionService.isLocalCIEnabled = true;
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        // No latest pending submission found.
        httpGetStub.mockReturnValue(of(undefined));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        // New submission comes in.
        wsSubmissionSubject.next(currentProgrammingSubmissionOld);
        wsSubmissionSubject.next(currentProgrammingSubmission);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmissionOld, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmission, participationId },
        ]);
        // old submission is now building.
        wsSubmissionProcessingSubject.next(mockSubmissionProcessingDTOOld);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmissionOld, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmission, participationId },
        ]);
        // new submission is now building.
        wsSubmissionProcessingSubject.next(mockSubmissionProcessingDTO);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmissionOld, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmission, participationId },
            {
                submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION,
                submission: currentProgrammingSubmission,
                participationId,
                buildTimingInfo: buildTimingInfo,
            },
        ]);
    });

    it('should change to building when queue timer ends', fakeAsync(() => {
        // @ts-ignore
        submissionService.currentExpectedQueueEstimate = 1000;
        submissionService.isLocalCIEnabled = true;
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        // No latest pending submission found.
        httpGetStub.mockReturnValue(of(undefined));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        // New submission comes in.
        currentProgrammingSubmission.submissionDate = dayjs();
        wsSubmissionSubject.next(currentProgrammingSubmission);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmission, participationId },
        ]);

        tick(1000);

        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmission, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentProgrammingSubmission, participationId },
        ]);

        wsSubmissionProcessingSubject.next(mockSubmissionProcessingDTO);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_QUEUED, submission: currentProgrammingSubmission, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentProgrammingSubmission, participationId },
        ]);

        discardPeriodicTasks();
    }));
});
