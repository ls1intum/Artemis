import { async } from '@angular/core/testing';
import * as chai from 'chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { BehaviorSubject, Subject } from 'rxjs';
import * as sinonChai from 'sinon-chai';
import { Participation, ParticipationWebsocketService } from '../../../../main/webapp/app/entities/participation';
import { MockWebsocketService } from '../mocks/mock-websocket.service';
import { IWebsocketService } from '../../../../main/webapp/app/core';
import { Result } from '../../../../main/webapp/app/entities/result';

chai.use(sinonChai);
const expect = chai.expect;

describe('ParticipationWebsocketService', () => {
    let websocketService: IWebsocketService;
    let receiveResultForParticipationSubject: Subject<Result>;
    let receiveParticipationSubject: Subject<Participation>;
    let receiveResultForParticipation2Subject: Subject<Result>;
    let receiveParticipation2Subject: Subject<Participation>;
    let subscribeSpy: SinonSpy;
    let receiveStub: SinonStub;
    let unsubscribeSpy: SinonSpy;

    let participationWebsocketService: ParticipationWebsocketService;

    const participation = { id: 1, exercise: { id: 20 } } as Participation;
    const currentResult = { id: 10, participation } as Result;
    participation.results = [currentResult];
    const newRatedResult = { id: 11, rated: true, participation } as Result;
    const newUnratedResult = { id: 12, rated: false, participation } as Result;

    const participationResultTopic = `/topic/participation/${participation.id}/newResults`;
    const participationTopic = `/user/topic/exercise/${participation.exercise.id}/participation`;

    const participation2 = { id: 2, exercise: { id: 40 } } as Participation;
    const currentResult2 = { id: 13, participation: participation2 } as Result;
    participation2.results = [currentResult2];

    const participation2ResultTopic = `/topic/participation/${participation2.id}/newResults`;
    const participation2Topic = `/user/topic/exercise/${participation2.exercise.id}/participation`;

    beforeEach(async(() => {
        websocketService = new MockWebsocketService();
        participationWebsocketService = new ParticipationWebsocketService(websocketService);

        subscribeSpy = spy(websocketService, 'subscribe');
        unsubscribeSpy = spy(websocketService, 'unsubscribe');
        receiveStub = stub(websocketService, 'receive');

        receiveResultForParticipationSubject = new Subject();
        receiveResultForParticipation2Subject = new Subject();
        receiveParticipationSubject = new Subject();
        receiveParticipation2Subject = new Subject();
        receiveStub.withArgs(participationResultTopic).returns(receiveResultForParticipationSubject);
        receiveStub.withArgs(participation2ResultTopic).returns(receiveResultForParticipation2Subject);
        receiveStub.withArgs(participationTopic).returns(receiveParticipationSubject);
        receiveStub.withArgs(participation2Topic).returns(receiveParticipation2Subject);
    }));

    afterEach(() => {
        subscribeSpy.restore();
        unsubscribeSpy.restore();
        receiveStub.restore();
    });

    it('should setup a result subscriptions with the websocket service on subscribeForLatestResult', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id);
        expect(subscribeSpy).to.have.been.calledOnce;
        expect(receiveStub).to.have.been.calledOnce;
        expect(unsubscribeSpy).not.to.have.been.called;

        expect(subscribeSpy).to.have.been.calledWithExactly(participationResultTopic);
        expect(receiveStub).to.have.been.calledWithExactly(participationResultTopic);

        expect(participationWebsocketService.cachedParticipations.size).to.equal(0);

        expect(participationWebsocketService.openWebsocketSubscriptions.size).to.equal(1);

        expect(participationWebsocketService.resultObservables.size).to.equal(1);
        expect(participationWebsocketService.resultObservables.get(participation.id)).to.exist;

        expect(participationWebsocketService.participationObservable).to.be.undefined;
    });

    it('should emit rated result when received through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id);
        const resultObservable = new BehaviorSubject(null);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id, resultObservable);

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(resultSpy).to.have.been.calledOnceWithExactly(newRatedResult);
    });

    it('should emit unrated result received through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id);
        const resultObservable = new BehaviorSubject(null);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id, resultObservable);

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newUnratedResult);

        expect(resultSpy).to.have.been.calledOnceWithExactly(newUnratedResult);
    });

    it('should also emit participation update with new result when new rated result arrives through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(null);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id, resultObservable);
        const participationObservable = new BehaviorSubject(null);
        const participationSpy = spy(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(resultSpy).to.have.been.calledOnce;
        expect(participationSpy).to.have.been.calledOnceWithExactly({ ...participation, results: [...participation.results, newRatedResult] });
        expect(participationWebsocketService.cachedParticipations.get(participation.id)).to.deep.equal({ ...participation, results: [...participation.results, newRatedResult] });
    });

    it('should emit participation update with new result when unrated result arrives through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(null);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id, resultObservable);
        const participationObservable = new BehaviorSubject(null);
        const participationSpy = spy(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newUnratedResult);

        expect(resultSpy).to.have.been.calledOnceWithExactly(newUnratedResult);
        expect(participationSpy).to.have.been.calledOnceWithExactly({ ...participation, results: [...participation.results, newUnratedResult] });
        expect(participationWebsocketService.cachedParticipations.get(participation.id)).to.deep.equal({ ...participation, results: [...participation.results, newUnratedResult] });
    });

    it('should attach the result to right participation if multiple participations are cached', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id);
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation2.id);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.addParticipation(participation2);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(null);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id, resultObservable);
        const participationObservable = new BehaviorSubject(null);
        const participationSpy = spy(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(participationWebsocketService.cachedParticipations.size).to.equal(2);
        expect(participationWebsocketService.cachedParticipations.get(participation.id)).to.deep.equal({ ...participation, results: [...participation.results, newRatedResult] });
        expect(participationWebsocketService.cachedParticipations.get(participation2.id)).to.deep.equal(participation2);

        expect(resultSpy).to.have.been.calledOnceWithExactly(newRatedResult);
        expect(participationSpy).to.have.been.calledOnceWithExactly({ ...participation, results: [...participation.results, newRatedResult] });
    });

    it('should attach the result to participation if the participation has null for results value', () => {
        const { results, ...participationWithoutResult } = participation;
        participationWebsocketService.subscribeForLatestResultOfParticipation(participationWithoutResult.id);
        participationWebsocketService.addParticipation(participationWithoutResult as Participation);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(null);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participationWithoutResult.id, resultObservable);
        const participationObservable = new BehaviorSubject(null);
        const participationSpy = spy(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(participationWebsocketService.cachedParticipations.size).to.equal(1);
        expect(participationWebsocketService.cachedParticipations.get(participationWithoutResult.id)).to.deep.equal({ ...participationWithoutResult, results: [newRatedResult] });

        expect(resultSpy).to.have.been.calledOnceWithExactly(newRatedResult);
        expect(participationSpy).to.have.been.calledOnceWithExactly({ ...participationWithoutResult, results: [newRatedResult] });
    });
});
