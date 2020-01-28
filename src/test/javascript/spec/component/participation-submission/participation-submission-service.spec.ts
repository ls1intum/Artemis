import { async } from '@angular/core/testing';
import * as chai from 'chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { of, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import * as sinonChai from 'sinon-chai';
import { MockHttpService } from '../../mocks/mock-http.service';
import { Submission, SubmissionExerciseType, SubmissionType } from 'app/entities/submission';
import { SubmissionService } from 'app/entities/submission/submission.service';
import { TextSubmission } from 'app/entities/text-submission';
import { Participation } from 'app/entities/participation';
import { MockWebsocketService } from '../../mocks/mock-websocket.service';
import { IWebsocketService } from 'app/core/websocket/websocket.service.ts';

chai.use(sinonChai);
const expect = chai.expect;

describe('SubmissionService', () => {
    let websocketService: IWebsocketService;
    let httpService: MockHttpService;
    let participationSubject: Subject<Participation>;
    let receiveSubmissionSubject: Subject<Submission>;
    let subscribeSpy: SinonSpy;
    let receiveStub: SinonStub;
    let unsubscribeSpy: SinonSpy;
    let getStub: SinonStub;

    let submissionService: SubmissionService;
    const participation = { id: 1 };

    const submission = {
        submissionExerciseType: SubmissionExerciseType.TEXT,
        id: 2278,
        submitted: true,
        type: SubmissionType.MANUAL,
        text: 'asdfasdfasdfasdf',
    } as TextSubmission;

    const url = `api/participations/${participation.id}/submissions`;

    beforeEach(async(() => {
        httpService = new MockHttpService();
        submissionService = new SubmissionService(httpService as any);
        websocketService = new MockWebsocketService();

        subscribeSpy = spy(websocketService, 'subscribe');
        unsubscribeSpy = spy(websocketService, 'unsubscribe');
        receiveStub = stub(websocketService, 'receive');
        getStub = stub(httpService, 'get');

        participationSubject = new Subject();
        receiveSubmissionSubject = new Subject();
        receiveStub.withArgs(url).returns(receiveSubmissionSubject);
        getStub.withArgs(`${submissionService.resourceUrlParticipation}/${participation.id}/submissions`).returns(of(submission));
    }));

    afterEach(() => {
        subscribeSpy.restore();
        unsubscribeSpy.restore();
        receiveStub.restore();
        getStub.restore();
    });

    it('Fetch data and check if route is correct', () => {
        let testSubmission;
        submissionService
            .findAllSubmissionsOfParticipation(participation.id)
            .pipe(tap(submission => (testSubmission = submission)))
            .subscribe();
        expect(getStub).to.have.been.calledOnce;
        expect(getStub.args[0][0]).to.equal('undefined' + url);
        expect(testSubmission).to.deep.equal(submission);
    });
});
