import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { IProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission/programming-submission.service';
import { IParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { MockProgrammingSubmissionService } from '../mocks/mock-programming-submission.service';
import { Result } from 'app/entities/result.model';
import { BehaviorSubject, of } from 'rxjs';
import { Feedback } from 'app/entities/feedback.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { IBuildLogService } from 'app/exercises/programming/assess/programming-assessment/build-logs/build-log.service';
import { MockParticipationWebsocketService } from '../mocks/mock-participation-websocket.service';
import { MockCodeEditorBuildLogService } from '../mocks/mock-code-editor-build-log.service';
import { OrionBuildAndTestService } from 'app/shared/orion/orion-build-and-test.service';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { MockOrionConnectorService } from '../mocks/mock-orion-connector.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('IdeBuildAndTestService', () => {
    let submissionService: IProgrammingSubmissionService;
    let participationService: IParticipationWebsocketService;
    let javaBridge: OrionConnectorService;
    let buildLogService: IBuildLogService;
    let ideBuildAndTestService: OrionBuildAndTestService;

    let onBuildFinishedSpy: SinonSpy;
    let onBuildStartedSpy: SinonSpy;
    let onTestResultSpy: SinonSpy;
    let onBuildFailedSpy: SinonSpy;
    let buildLogsStub: SinonStub;
    let participationSubscriptionStub: SinonStub;

    const feedbacks = [{ id: 2, positive: false, detailText: 'abc' } as Feedback, { id: 3, positive: true, detailText: 'cde' } as Feedback];
    const result = { id: 1 } as Result;
    const exercise = { id: 42, studentParticipations: [{ id: 32 }] } as ProgrammingExercise;

    beforeEach(() => {
        submissionService = new MockProgrammingSubmissionService();
        participationService = new MockParticipationWebsocketService();
        javaBridge = new MockOrionConnectorService();
        buildLogService = new MockCodeEditorBuildLogService();

        ideBuildAndTestService = new OrionBuildAndTestService(submissionService, participationService, javaBridge, buildLogService);

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
        result.feedbacks = [];
        result.successful = false;
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
