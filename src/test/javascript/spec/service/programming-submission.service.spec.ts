import dayjs from 'dayjs/esm';
import { BehaviorSubject, Subject, lastValueFrom, of } from 'rxjs';
import { range as _range } from 'lodash-es';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import {
    ExerciseSubmissionState,
    ProgrammingSubmissionService,
    ProgrammingSubmissionState,
    ProgrammingSubmissionStateObj,
} from 'app/exercises/programming/participate/programming-submission.service';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Result } from 'app/entities/result.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { MockParticipationWebsocketService } from '../helpers/mocks/service/mock-participation-websocket.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from '../helpers/mocks/service/mock-programming-exercise-participation.service';
import { HttpClient } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('ProgrammingSubmissionService', () => {
    let websocketService: JhiWebsocketService;
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
    let wsLatestResultSubject: Subject<Result | undefined>;

    const participationId = 1;
    const submissionTopic = `/user/topic/newSubmissions`;
    let currentSubmission: Submission;
    let currentSubmission2: Submission;
    let result: Result;
    let result2: Result;

    beforeEach(() => {
        currentSubmission = { id: 11, submissionDate: dayjs().subtract(20, 'seconds'), participation: { id: participationId } } as any;
        currentSubmission2 = { id: 12, submissionDate: dayjs().subtract(20, 'seconds'), participation: { id: participationId } } as any;
        result = { id: 31, submission: currentSubmission } as any;
        result2 = { id: 32, submission: currentSubmission2 } as any;

        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
            ],
        })
            .compileComponents()
            .then(() => {
                submissionService = TestBed.inject(ProgrammingSubmissionService);
                websocketService = TestBed.inject(JhiWebsocketService);
                httpService = TestBed.inject(HttpClient);
                participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
                participationService = TestBed.inject(ProgrammingExerciseParticipationService);

                httpMock = TestBed.inject(HttpTestingController);
                httpGetStub = jest.spyOn(httpService, 'get');
                wsSubscribeStub = jest.spyOn(websocketService, 'subscribe');
                wsUnsubscribeStub = jest.spyOn(websocketService, 'unsubscribe');
                wsSubmissionSubject = new Subject<Submission | undefined>();
                wsReceiveStub = jest.spyOn(websocketService, 'receive').mockReturnValue(wsSubmissionSubject);
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
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((sub) => (submission = sub));
        expect(submission).toEqual({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId });
        expect(wsSubscribeStub).toHaveBeenCalledOnce();
        expect(wsSubscribeStub).toHaveBeenCalledWith(submissionTopic);
        expect(wsReceiveStub).toHaveBeenCalledOnce();
        expect(wsReceiveStub).toHaveBeenCalledWith(submissionTopic);
        expect(participationWsLatestResultStub).toHaveBeenCalledOnce();
        expect(participationWsLatestResultStub).toHaveBeenCalledWith(participationId, true, 10);
    });

    it('should emit undefined when a new result comes in for the given participation to signal that the building process is over', () => {
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        httpGetStub.mockReturnValue(of(currentSubmission));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId }]);
        // Result comes in for submission.
        result.submission = currentSubmission;
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).toEqual([
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    });

    it('should NOT emit undefined when a new result comes that does not belong to the currentSubmission', () => {
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        httpGetStub.mockReturnValue(of(currentSubmission));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId }]);
        // Result comes in for submission.
        result.submission = currentSubmission2;
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).toEqual([{ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId }]);
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
        expect(getLatestResultStub).toHaveBeenCalledWith(participationId, true);

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
        expect(getLatestResultStub).toHaveBeenCalledWith(participationId, true);
        expect(notifyAllResultSubscribersStub).toHaveBeenCalledOnce();
        expect(notifyAllResultSubscribersStub).toHaveBeenCalledWith({ ...result, participation: { id: participationId } });
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
        const request = httpMock.expectOne('api/programming-exercises/3/latest-pending-submissions');

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
        expect(httpGetStub).toHaveBeenCalledWith(`api/programming-exercises/${exerciseId}/latest-pending-submissions`);
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
        expect(httpGetStub).toHaveBeenCalledWith(`api/programming-exercises/${exerciseId}/latest-pending-submissions`);
    });

    it('should recalculate the result eta based on the number of open submissions', () => {
        const exerciseId = 10;
        // Simulate 340 participations with one pending submission each.
        const submissionState = _range(340).reduce((acc, n) => ({ ...acc, [n]: { submissionDate: dayjs().subtract(1, 'minutes') } as ProgrammingSubmission }), {});
        const expectedSubmissionState = Object.entries(submissionState).reduce(
            (acc, [participationID, submission]: [string, ProgrammingSubmission]) => ({
                ...acc,
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
        expect(httpGetStub).toHaveBeenCalledWith(`api/programming-exercises/${exerciseId}/latest-pending-submissions`);

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
});
