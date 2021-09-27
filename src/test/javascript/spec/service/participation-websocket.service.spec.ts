import { async } from '@angular/core/testing';
import * as chai from 'chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { BehaviorSubject, Subject } from 'rxjs';
import sinonChai from 'sinon-chai';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { IWebsocketService } from 'app/core/websocket/websocket.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ParticipationWebsocketService', () => {
    let websocketService: IWebsocketService;
    // tslint:disable-next-line:prefer-const
    let participationService: ParticipationService;
    let receiveParticipationSubject: Subject<Participation>;
    let receiveParticipation2Subject: Subject<Participation>;
    let receiveResultForParticipationSubject: Subject<Result>;
    let receiveResultForParticipation2Subject: Subject<Result>;
    let subscribeSpy: SinonSpy;
    let receiveStub: SinonStub;
    let unsubscribeSpy: SinonSpy;

    let participationWebsocketService: ParticipationWebsocketService;

    const exerciseId1 = 20;
    const exerciseId2 = 40;

    const participation = { id: 1, exercise: { id: exerciseId1 } } as Participation;
    const currentResult = { id: 10, participation } as Result;
    participation.results = [currentResult];
    const newRatedResult = { id: 11, rated: true, participation } as Result;
    const newUnratedResult = { id: 12, rated: false, participation } as Result;

    const participationPersonalResultTopic = `/user/topic/newResults`;
    const participationTopic = `/user/topic/exercise/${participation.exercise!.id}/participation`;

    const participation2 = { id: 2, exercise: { id: exerciseId2 } } as Participation;
    const currentResult2 = { id: 13, participation: participation2 } as Result;
    participation2.results = [currentResult2];

    const participationInstructorResultTopic = `/topic/exercise/${participation2.exercise!.id}/newResults`;
    const participation2Topic = `/user/topic/exercise/${participation2.exercise!.id}/participation`;

    beforeEach(async(() => {
        websocketService = new MockWebsocketService();
        // @ts-ignore
        participationWebsocketService = new ParticipationWebsocketService(websocketService, participationService);

        subscribeSpy = spy(websocketService, 'subscribe');
        unsubscribeSpy = spy(websocketService, 'unsubscribe');
        receiveStub = stub(websocketService, 'receive');

        receiveResultForParticipationSubject = new Subject();
        receiveResultForParticipation2Subject = new Subject();
        receiveParticipationSubject = new Subject();
        receiveParticipation2Subject = new Subject();
        receiveStub.withArgs(participationPersonalResultTopic).returns(receiveResultForParticipationSubject);
        receiveStub.withArgs(participationInstructorResultTopic).returns(receiveResultForParticipation2Subject);
        receiveStub.withArgs(participationTopic).returns(receiveParticipationSubject);
        receiveStub.withArgs(participation2Topic).returns(receiveParticipation2Subject);
    }));

    afterEach(() => {
        subscribeSpy.restore();
        unsubscribeSpy.restore();
        receiveStub.restore();
    });

    it('should setup a result subscriptions with the websocket service on subscribeForLatestResult for instructors', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, false, exerciseId2);
        expect(subscribeSpy).to.have.been.calledOnce;
        expect(receiveStub).to.have.been.calledOnce;
        expect(unsubscribeSpy).not.to.have.been.called;

        expect(subscribeSpy).to.have.been.calledWithExactly(participationInstructorResultTopic);
        expect(receiveStub).to.have.been.calledWithExactly(participationInstructorResultTopic);

        expect(participationWebsocketService.cachedParticipations.size).to.equal(0);

        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).to.equal(1);
        expect(participationWebsocketService.openPersonalWebsocketSubscription).to.be.undefined;

        expect(participationWebsocketService.resultObservables.size).to.equal(1);
        expect(participationWebsocketService.resultObservables.get(participation.id!)).to.exist;

        expect(participationWebsocketService.participationObservable).to.be.undefined;
    });

    it('should setup a result subscriptions with the websocket service on subscribeForLatestResult for students', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        expect(subscribeSpy).to.have.been.calledOnce;
        expect(receiveStub).to.have.been.calledOnce;
        expect(unsubscribeSpy).not.to.have.been.called;

        expect(subscribeSpy).to.have.been.calledWithExactly(participationPersonalResultTopic);
        expect(receiveStub).to.have.been.calledWithExactly(participationPersonalResultTopic);

        expect(participationWebsocketService.cachedParticipations.size).to.equal(0);

        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).to.equal(0);
        expect(participationWebsocketService.openPersonalWebsocketSubscription).to.equal(participationPersonalResultTopic);

        expect(participationWebsocketService.resultObservables.size).to.equal(1);
        expect(participationWebsocketService.resultObservables.get(participation.id!)).to.exist;

        expect(participationWebsocketService.participationObservable).to.be.undefined;
    });

    it('should emit rated result when received through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(resultSpy).to.have.been.calledOnceWithExactly(newRatedResult);
    });

    it('should emit unrated result received through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newUnratedResult);

        expect(resultSpy).to.have.been.calledOnceWithExactly(newUnratedResult);
    });

    it('should also emit participation update with new result when new rated result arrives through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);
        const participationObservable = new BehaviorSubject(undefined);
        const participationSpy = spy(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(resultSpy).to.have.been.calledOnce;
        expect(participationSpy).to.have.been.calledOnceWithExactly({ ...participation, results: [...participation.results!, newRatedResult] });
        expect(participationWebsocketService.cachedParticipations.get(participation.id!)).to.deep.equal({ ...participation, results: [...participation.results!, newRatedResult] });
    });

    it('should emit participation update with new result when unrated result arrives through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);
        const participationObservable = new BehaviorSubject(undefined);
        const participationSpy = spy(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newUnratedResult);

        expect(resultSpy).to.have.been.calledOnceWithExactly(newUnratedResult);
        expect(participationSpy).to.have.been.calledOnceWithExactly({ ...participation, results: [...participation.results!, newUnratedResult] });
        expect(participationWebsocketService.cachedParticipations.get(participation.id!)).to.deep.equal({
            ...participation,
            results: [...participation.results!, newUnratedResult],
        });
    });

    it('should attach the result to right participation if multiple participations are cached', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation2.id!, true);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.addParticipation(participation2);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);
        const participationObservable = new BehaviorSubject(undefined);
        const participationSpy = spy(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(participationWebsocketService.cachedParticipations.size).to.equal(2);
        expect(participationWebsocketService.cachedParticipations.get(participation.id!)).to.deep.equal({ ...participation, results: [...participation.results!, newRatedResult] });
        expect(participationWebsocketService.cachedParticipations.get(participation2.id!)).to.deep.equal(participation2);

        expect(resultSpy).to.have.been.calledOnceWithExactly(newRatedResult);
        expect(participationSpy).to.have.been.calledOnceWithExactly({ ...participation, results: [...participation.results!, newRatedResult] });
    });

    /* eslint-disable no-unused-vars */
    it('should attach the result to participation if the participation has undefined for results value', () => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const { results, ...participationWithoutResult } = participation;

        participationWebsocketService.subscribeForLatestResultOfParticipation(participationWithoutResult.id!, true);
        participationWebsocketService.addParticipation(participationWithoutResult as Participation);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = spy(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participationWithoutResult.id!, resultObservable);
        const participationObservable = new BehaviorSubject(undefined);
        const participationSpy = spy(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(participationWebsocketService.cachedParticipations.size).to.equal(1);
        expect(participationWebsocketService.cachedParticipations.get(participationWithoutResult.id!)).to.deep.equal({ ...participationWithoutResult, results: [newRatedResult] });

        expect(resultSpy).to.have.been.calledOnceWithExactly(newRatedResult);
        expect(participationSpy).to.have.been.calledOnceWithExactly({ ...participationWithoutResult, results: [newRatedResult] });
    });
    /* eslint-enable no-unused-vars */

    it('should reset the local cache', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation2.id!, false, participation2.exercise!.id);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.addParticipation(participation2);

        expect(participationWebsocketService.openPersonalWebsocketSubscription).to.equal(participationPersonalResultTopic);
        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).to.equal(1);

        participationWebsocketService.resetLocalCache();

        expect(participationWebsocketService.openPersonalWebsocketSubscription).to.be.undefined;
        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).to.equal(0);
    });

    it('should return the cached participation after adding it', () => {
        expect(participationWebsocketService.getParticipationForExercise(participation.exercise!.id!)).to.be.undefined;

        participationWebsocketService.addParticipation(participation);

        expect(participationWebsocketService.getParticipationForExercise(participation.exercise!.id!)).to.deep.equal(participation);
    });
});
