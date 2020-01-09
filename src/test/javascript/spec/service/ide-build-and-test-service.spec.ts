import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { ParticipationWebsocketService } from 'app/entities/participation/participation-websocket.service';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { CodeEditorBuildLogService } from 'app/code-editor';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { MockProgrammingSubmissionService } from '../mocks/mock-programming-submission.service';
import { MockCodeEditorBuildLogService, MockParticipationWebsocketService } from '../mocks';
import { MockJavaBridgeService } from '../mocks/mock-java-bridge.service';
import { IdeBuildAndTestService } from 'app/intellij/ide-build-and-test.service';
import { Result } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';
import { BehaviorSubject, of } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { buildLogs } from '../sample/build-logs';

chai.use(sinonChai);
const expect = chai.expect;

describe('IdeBuildAndTestService', () => {
    let submissionService: ProgrammingSubmissionService;
    let participationService: ParticipationWebsocketService;
    let javaBridge: JavaBridgeService;
    let buildLogService: CodeEditorBuildLogService;
    let ideBuildAndTestService: IdeBuildAndTestService;

    let onBuildFinishedSpy: SinonSpy;
    let onBuildStartedSpy: SinonSpy;
    let onTestResultSpy: SinonSpy;
    let onBuildFailedSpy: SinonSpy;
    let buildLogsStub: SinonStub;
    let participationSubscriptionStub: SinonStub;

    const feedbacks = [
        { id: 2, positive: false, detailText: 'abc' },
        { id: 3, positive: true, detailText: 'cde' },
    ] as [Feedback];
    const result = { id: 1 } as Result;
    const exercise = { id: 42, studentParticipations: [{ id: 32 }] } as ProgrammingExercise;

    beforeEach(() => {
        submissionService = new MockProgrammingSubmissionService();
        participationService = new MockParticipationWebsocketService();
        javaBridge = new MockJavaBridgeService();
        buildLogService = new MockCodeEditorBuildLogService();

        ideBuildAndTestService = new IdeBuildAndTestService(submissionService, participationService, javaBridge, buildLogService);

        onBuildFinishedSpy = spy(javaBridge, 'onBuildFinished');
        onBuildStartedSpy = spy(javaBridge, 'onBuildStarted');
        onTestResultSpy = spy(javaBridge, 'onTestResult');
        onBuildFailedSpy = spy(javaBridge, 'onBuildFailed');
        buildLogsStub = stub(buildLogService, 'getBuildLogs');
        participationSubscriptionStub = stub(participationService, 'subscribeForLatestResultOfParticipation');
    });

    afterEach(() => {
        onBuildFinishedSpy.restore();
        onBuildStartedSpy.restore();
        onTestResultSpy.restore();
        onBuildFailedSpy.restore();
        buildLogsStub.restore();
        participationSubscriptionStub.restore();
    });

    it('should forward all results for positive results', () => {
        result.feedbacks = feedbacks;
        result.successful = true;
        result.hasFeedback = true;
        const subject = new BehaviorSubject(result);
        participationSubscriptionStub.returns(subject);

        ideBuildAndTestService.listenOnBuildOutputAndForwardChanges(exercise);

        expect(participationSubscriptionStub).to.have.been.calledOnceWithExactly(exercise.studentParticipations[0].id);
        expect(onBuildStartedSpy).to.have.been.called.calledOnce;
        expect(onTestResultSpy).to.have.been.calledTwice;
        expect(onBuildFinishedSpy).to.have.been.calledOnce;
        expect(onBuildFailedSpy).to.not.have.been.called;
        expect(buildLogsStub).to.not.have.been.called;
    });

    it('should should forward all results for negative results', () => {
        result.feedbacks = feedbacks;
        result.successful = false;
        result.hasFeedback = true;
        const subject = new BehaviorSubject(result);
        participationSubscriptionStub.returns(subject);

        ideBuildAndTestService.listenOnBuildOutputAndForwardChanges(exercise);

        expect(participationSubscriptionStub).to.have.been.calledOnceWithExactly(exercise.studentParticipations[0].id);
        expect(onBuildStartedSpy).to.have.been.called.calledOnce;
        expect(onTestResultSpy).to.have.been.calledTwice;
        expect(onBuildFinishedSpy).to.have.been.calledOnce;
        expect(onBuildFailedSpy).to.not.have.been.called;
        expect(buildLogsStub).to.not.have.been.called;
    });

    it('should forward compile errors if the build failed', () => {
        const logs = [
            {
                time: '2019-05-15T10:32:11+02:00',
                log: '[ERROR] COMPILATION ERROR : ',
            },
        ];
        buildLogsStub.returns(of(logs));
        result.successful = false;
        result.feedbacks = undefined;
        result.hasFeedback = false;
        const subject = new BehaviorSubject(result);
        participationSubscriptionStub.returns(subject);

        ideBuildAndTestService.listenOnBuildOutputAndForwardChanges(exercise);

        expect(participationSubscriptionStub).to.have.been.calledOnceWithExactly(exercise.studentParticipations[0].id);
        expect(onBuildStartedSpy).to.have.been.called.calledOnce;
        expect(onTestResultSpy).to.not.have.been.called;
        expect(onBuildFinishedSpy).to.not.have.been.called;
        expect(onBuildFailedSpy).to.have.been.calledOnce;
        expect(buildLogsStub).to.have.been.calledOnce;
    });
});
