import * as chai from 'chai';
import * as moment from 'moment';
import { SinonStub, spy, stub } from 'sinon';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { range as _range } from 'lodash';
import * as sinonChai from 'sinon-chai';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import {
    ExerciseSubmissionState,
    IProgrammingSubmissionService,
    ProgrammingSubmissionService,
    ProgrammingSubmissionState,
    ProgrammingSubmissionStateObj,
} from 'app/exercises/programming/participate/programming-submission.service';
import { IParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockAlertService } from '../helpers/mocks/service/mock-alert.service';
import { Result } from 'app/entities/result.model';
import { SERVER_API_URL } from 'app/app.constants';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Submission } from 'app/entities/submission.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { MockParticipationWebsocketService } from '../helpers/mocks/service/mock-participation-websocket.service';
import { IProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from '../helpers/mocks/service/mock-programming-exercise-participation.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingSubmissionService', () => {
    let websocketService: MockWebsocketService;
    let httpService: MockHttpService;
    let participationWebsocketService: IParticipationWebsocketService;
    let alertService: MockAlertService;
    let participationService: IProgrammingExerciseParticipationService;
    let submissionService: IProgrammingSubmissionService;

    let httpGetStub: SinonStub;
    let wsSubscribeStub: SinonStub;
    let wsUnsubscribeStub: SinonStub;
    let wsReceiveStub: SinonStub;
    let participationWsLatestResultStub: SinonStub;
    let getLatestResultStub: SinonStub;
    let notifyAllResultSubscribersStub: SinonStub;

    let wsSubmissionSubject: Subject<Submission | undefined>;
    let wsLatestResultSubject: Subject<Result | undefined>;

    const participationId = 1;
    const submissionTopic = `/user/topic/newSubmissions`;
    let currentSubmission: Submission;
    let currentSubmission2: Submission;
    let result: Result;
    let result2: Result;

    beforeEach(() => {
        currentSubmission = { id: 11, submissionDate: moment().subtract(20, 'seconds'), participation: { id: participationId } } as any;
        currentSubmission2 = { id: 12, submissionDate: moment().subtract(20, 'seconds'), participation: { id: participationId } } as any;
        result = { id: 31, submission: currentSubmission } as any;
        result2 = { id: 32, submission: currentSubmission2 } as any;

        websocketService = new MockWebsocketService();
        httpService = new MockHttpService();
        participationWebsocketService = new MockParticipationWebsocketService();
        alertService = new MockAlertService();
        participationService = new MockProgrammingExerciseParticipationService();

        httpGetStub = stub(httpService, 'get');
        wsSubscribeStub = stub(websocketService, 'subscribe');
        wsUnsubscribeStub = stub(websocketService, 'unsubscribe');
        wsSubmissionSubject = new Subject<Submission | undefined>();
        wsReceiveStub = stub(websocketService, 'receive').returns(wsSubmissionSubject);
        wsLatestResultSubject = new Subject<Result | undefined>();
        participationWsLatestResultStub = stub(participationWebsocketService, 'subscribeForLatestResultOfParticipation').returns(wsLatestResultSubject as any);
        getLatestResultStub = stub(participationService, 'getLatestResultWithFeedback');
        notifyAllResultSubscribersStub = stub(participationWebsocketService, 'notifyAllResultSubscribers');

        // @ts-ignore
        submissionService = new ProgrammingSubmissionService(websocketService, httpService, participationWebsocketService, participationService, alertService);
    });

    afterEach(() => {
        httpGetStub.restore();
        wsSubscribeStub.restore();
        wsUnsubscribeStub.restore();
        wsReceiveStub.restore();
        participationWsLatestResultStub.restore();
        getLatestResultStub.restore();
        notifyAllResultSubscribersStub.restore();
    });

    it('should return cached subject as Observable for provided participation if exists', () => {
        const cachedSubject = new BehaviorSubject(undefined);
        // @ts-ignore
        const fetchLatestPendingSubmissionSpy = spy(submissionService, 'fetchLatestPendingSubmissionByParticipationId');
        // @ts-ignore
        const setupWebsocketSubscriptionSpy = spy(submissionService, 'setupWebsocketSubscriptionForLatestPendingSubmission');
        // @ts-ignore
        const subscribeForNewResultSpy = spy(submissionService, 'subscribeForNewResult');
        // @ts-ignore
        submissionService.submissionSubjects = { [participationId]: cachedSubject };

        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true);
        expect(fetchLatestPendingSubmissionSpy).to.not.have.been.called;
        expect(setupWebsocketSubscriptionSpy).to.not.have.been.called;
        expect(subscribeForNewResultSpy).to.not.have.been.called;
    });

    it('should query httpService endpoint and setup the websocket subscriptions if no subject is cached for the provided participation', async () => {
        httpGetStub.returns(of(currentSubmission));
        const submission = await new Promise((resolve) => submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => resolve(s)));
        expect(submission).to.deep.equal({ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId });
        expect(wsSubscribeStub).to.have.been.calledOnceWithExactly(submissionTopic);
        expect(wsReceiveStub).to.have.been.calledOnceWithExactly(submissionTopic);
        expect(participationWsLatestResultStub).to.have.been.calledOnceWithExactly(participationId, true, 10);
    });

    it('should emit undefined when a new result comes in for the given participation to signal that the building process is over', () => {
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        httpGetStub.returns(of(currentSubmission));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).to.deep.equal([{ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId }]);
        // Result comes in for submission.
        result.submission = currentSubmission;
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).to.deep.equal([
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    });

    it('should NOT emit undefined when a new result comes that does not belong to the currentSubmission', () => {
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        httpGetStub.returns(of(currentSubmission));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).to.deep.equal([{ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId }]);
        // Result comes in for submission.
        result.submission = currentSubmission2;
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).to.deep.equal([{ submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId }]);
    });

    it('should emit the newest submission when it was received through the websocket connection', () => {
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        // No latest pending submission found.
        httpGetStub.returns(of(undefined));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        expect(returnedSubmissions).to.deep.equal([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        // New submission comes in.
        wsSubmissionSubject.next(currentSubmission2);
        expect(returnedSubmissions).to.deep.equal([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission2, participationId },
        ]);
        // Result comes in for submission.
        result.submission = currentSubmission2;
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).to.deep.equal([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission2, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    });

    it('should emit the failed submission state when the result waiting timer runs out AND accept a late result', async () => {
        // Set the timer to 10ms for testing purposes.
        // @ts-ignore
        submissionService.DEFAULT_EXPECTED_RESULT_ETA = 10;
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        httpGetStub.returns(of(undefined));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        // We simulate that the latest result from the server does not belong the pending submission
        getLatestResultStub = getLatestResultStub.returns(of(result));

        expect(returnedSubmissions).to.deep.equal([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        wsSubmissionSubject.next(currentSubmission2);
        expect(returnedSubmissions).to.deep.equal([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission2, participationId },
        ]);
        // Wait 10ms for the timeout
        await new Promise<void>((resolve) => setTimeout(() => resolve(), 10));

        // Expect the fallback mechanism to kick in after the timeout
        expect(getLatestResultStub).to.have.been.calledOnceWithExactly(participationId, true);

        // HAS_FAILED_SUBMISSION is expected as the result provided by getLatestResult does not match the pending submission
        expect(returnedSubmissions).to.deep.equal([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission2, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: currentSubmission2, participationId },
        ]);

        // Now the result for the submission in - should be accepted even though technically too late!
        wsLatestResultSubject.next(result2);
        expect(returnedSubmissions).to.deep.equal([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission2, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: currentSubmission2, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    });

    it('should emit has no pending submission if the fallback mechanism fetches the right result from the server', async () => {
        // Set the timer to 10ms for testing purposes.
        // @ts-ignore
        submissionService.DEFAULT_EXPECTED_RESULT_ETA = 10;
        const returnedSubmissions: Array<ProgrammingSubmissionStateObj | undefined> = [];
        httpGetStub.returns(of(undefined));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true).subscribe((s) => returnedSubmissions.push(s));
        // We simulate that the latest result from the server matches the pending submission
        getLatestResultStub = getLatestResultStub.returns(of(result));

        expect(returnedSubmissions).to.deep.equal([{ submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId }]);
        wsSubmissionSubject.next(currentSubmission);
        expect(returnedSubmissions).to.deep.equal([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId },
        ]);
        // Wait 10ms for the timeout.
        await new Promise<void>((resolve) => setTimeout(() => resolve(), 10));

        // Expect the fallback mechanism to kick in after the timeout
        expect(getLatestResultStub).to.have.been.calledOnceWithExactly(participationId, true);
        expect(notifyAllResultSubscribersStub).to.have.been.calledOnceWithExactly({ ...result, participation: { id: participationId } });
        wsLatestResultSubject.next(result);

        // HAS_NO_PENDING_SUBMISSION is expected as the result provided by getLatestResult matches the pending submission
        expect(returnedSubmissions).to.deep.equal([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);

        // If the "real" websocket connection triggers now for some reason, the submission state should not change
        wsLatestResultSubject.next(result);
        expect(returnedSubmissions).to.deep.equal([
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
            { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission: currentSubmission, participationId },
            { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId },
        ]);
    });

    it('should fetch the latest pending submissions for all participations of an exercise if preloadLatestPendingSubmissionsForExercise is called', async () => {
        const exerciseId = 3;
        const participation1 = { id: 2 } as StudentParticipation;
        const participation2 = { id: 3 } as StudentParticipation;
        // This participation will not be cached.
        const participation3 = { id: 4 } as StudentParticipation;
        let submissionState, submission;

        const pendingSubmissions = { [participation1.id!]: currentSubmission, [participation2.id!]: currentSubmission2 };

        // @ts-ignore
        const fetchLatestPendingSubmissionSpy = spy(submissionService, 'fetchLatestPendingSubmissionByParticipationId');

        httpGetStub.returns(of(pendingSubmissions));

        // This load the submissions for participation 1 and 2, but not for 3.
        submissionService.getSubmissionStateOfExercise(exerciseId).toPromise();
        submissionService.getLatestPendingSubmissionByParticipationId(participation1.id!, exerciseId, true).subscribe(({ submissionState: state, submission: sub }) => {
            submissionState = state;
            submission = sub;
        });

        expect(httpGetStub).to.have.been.calledOnceWithExactly('undefinedapi/programming-exercises/3/latest-pending-submissions');
        // Fetching the latest pending submission should not trigger a rest call for a cached submission.
        expect(fetchLatestPendingSubmissionSpy).not.to.have.been.called;
        expect(submissionState).to.equal(ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION);
        expect(submission).to.equal(currentSubmission);

        // Fetching the latest pending submission should trigger a rest call if the submission is not cached.
        await submissionService.getLatestPendingSubmissionByParticipationId(participation3.id!, exerciseId, true);
        expect(fetchLatestPendingSubmissionSpy).to.have.been.calledOnceWithExactly(participation3.id);
    });

    it('should not throw an error on getSubmissionStateOfExercise if state is an empty object', async () => {
        const exerciseId = 10;
        const submissionState: ExerciseSubmissionState = {};
        // @ts-ignore
        const fetchLatestPendingSubmissionsByExerciseIdSpy = spy(submissionService, 'fetchLatestPendingSubmissionsByExerciseId');
        httpGetStub.withArgs(SERVER_API_URL + `api/programming-exercises/${exerciseId}/latest-pending-submissions`).returns(of(submissionState));

        let receivedSubmissionState: ExerciseSubmissionState = {};
        submissionService.getSubmissionStateOfExercise(exerciseId).subscribe((state) => (receivedSubmissionState = state));
        expect(fetchLatestPendingSubmissionsByExerciseIdSpy).to.have.been.calledOnceWithExactly(exerciseId);
        expect(receivedSubmissionState).to.deep.equal(submissionState);
    });

    it('should correctly return the submission state based on the servers response', async () => {
        const exerciseId = 10;
        const submissionState = { 1: { id: 11, submissionDate: moment().subtract(2, 'hours') } as ProgrammingSubmission, 2: undefined };
        const expectedSubmissionState = {
            1: { submissionState: ProgrammingSubmissionState.HAS_FAILED_SUBMISSION, submission: submissionState['1'], participationId: 1 },
            2: { submissionState: ProgrammingSubmissionState.HAS_NO_PENDING_SUBMISSION, submission: undefined, participationId: 2 },
        };
        // @ts-ignore
        const fetchLatestPendingSubmissionsByExerciseIdSpy = spy(submissionService, 'fetchLatestPendingSubmissionsByExerciseId');
        httpGetStub.withArgs(SERVER_API_URL + `api/programming-exercises/${exerciseId}/latest-pending-submissions`).returns(of(submissionState));

        let receivedSubmissionState: ExerciseSubmissionState = {};
        submissionService.getSubmissionStateOfExercise(exerciseId).subscribe((state) => (receivedSubmissionState = state));
        expect(fetchLatestPendingSubmissionsByExerciseIdSpy).to.have.been.calledOnceWithExactly(exerciseId);
        expect(receivedSubmissionState).to.deep.equal(expectedSubmissionState);
    });

    it('should recalculate the result eta based on the number of open submissions', () => {
        const exerciseId = 10;
        // Simulate 340 participations with one pending submission each.
        const submissionState = _range(340).reduce((acc, n) => ({ ...acc, [n]: { submissionDate: moment().subtract(1, 'minutes') } as ProgrammingSubmission }), {});
        const expectedSubmissionState = Object.entries(submissionState).reduce(
            (acc, [participationID, submission]: [string, ProgrammingSubmission]) => ({
                ...acc,
                [participationID]: { submissionState: ProgrammingSubmissionState.IS_BUILDING_PENDING_SUBMISSION, submission, participationId: parseInt(participationID, 10) },
            }),
            {},
        );
        // @ts-ignore
        const fetchLatestPendingSubmissionsByExerciseIdSpy = spy(submissionService, 'fetchLatestPendingSubmissionsByExerciseId');
        httpGetStub.withArgs(SERVER_API_URL + `api/programming-exercises/${exerciseId}/latest-pending-submissions`).returns(of(submissionState));

        let receivedSubmissionState: ExerciseSubmissionState = {};
        submissionService.getSubmissionStateOfExercise(exerciseId).subscribe((state) => (receivedSubmissionState = state));
        expect(fetchLatestPendingSubmissionsByExerciseIdSpy).to.have.been.calledOnceWithExactly(exerciseId);
        expect(receivedSubmissionState).to.deep.equal(expectedSubmissionState);

        let resultEta = -1;
        submissionService.getResultEtaInMs().subscribe((eta) => (resultEta = eta));

        // With 340 submissions, the eta should now have increased.
        expect(resultEta).to.equal(2000 * 60 + 3 * 4000 * 60);
    });

    it('should only unsubscribe if no other participations use the topic', () => {
        httpGetStub.returns(of(currentSubmission));
        submissionService.getLatestPendingSubmissionByParticipationId(participationId, 10, true);
        submissionService.getLatestPendingSubmissionByParticipationId(2, 10, true);

        // Should not unsubscribe as participation 2 still uses the same topic
        submissionService.unsubscribeForLatestSubmissionOfParticipation(participationId);
        expect(wsUnsubscribeStub).to.not.have.been.called;

        // Should now unsubscribe as last participation for topic was unsubscribed
        submissionService.unsubscribeForLatestSubmissionOfParticipation(2);
        expect(wsUnsubscribeStub).to.have.been.called;
    });
});
