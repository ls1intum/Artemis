import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject } from 'rxjs';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Participation } from 'app/entities/participation/participation.model';
import { Result } from 'app/entities/result.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { MockParticipationService } from '../helpers/mocks/service/mock-participation.service';

describe('ParticipationWebsocketService', () => {
    let websocketService: JhiWebsocketService;
    let receiveParticipationSubject: Subject<Participation>;
    let receiveParticipation2Subject: Subject<Participation>;
    let receiveResultForParticipationSubject: Subject<Result>;
    let receiveResultForParticipation2Subject: Subject<Result>;
    let subscribeSpy: jest.SpyInstance;
    let receiveStub: jest.SpyInstance;
    let unsubscribeSpy: jest.SpyInstance;

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

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: ParticipationService, useClass: MockParticipationService },
            ],
        })
            .compileComponents()
            .then(() => {
                participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
                websocketService = TestBed.inject(JhiWebsocketService);

                subscribeSpy = jest.spyOn(websocketService, 'subscribe');
                unsubscribeSpy = jest.spyOn(websocketService, 'unsubscribe');
                receiveStub = jest.spyOn(websocketService, 'receive');

                receiveResultForParticipationSubject = new Subject();
                receiveResultForParticipation2Subject = new Subject();
                receiveParticipationSubject = new Subject();
                receiveParticipation2Subject = new Subject();
                receiveStub.mockImplementation((arg1) => {
                    switch (arg1) {
                        case participationPersonalResultTopic:
                            return receiveResultForParticipationSubject;
                        case participationInstructorResultTopic:
                            return receiveResultForParticipation2Subject;
                        case participationTopic:
                            return receiveParticipationSubject;
                        case participation2Topic:
                            return receiveParticipation2Subject;
                    }
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should setup a result subscriptions with the websocket service on subscribeForLatestResult for instructors', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, false, exerciseId2);
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(subscribeSpy).toHaveBeenCalledWith(participationInstructorResultTopic);
        expect(receiveStub).toHaveBeenCalledOnce();
        expect(receiveStub).toHaveBeenCalledWith(participationInstructorResultTopic);
        expect(unsubscribeSpy).not.toHaveBeenCalled();

        expect(participationWebsocketService.cachedParticipations.size).toBe(0);

        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).toBe(1);
        expect(participationWebsocketService.openPersonalWebsocketSubscription).toBe(undefined);

        expect(participationWebsocketService.resultObservables.size).toBe(1);
        expect(participationWebsocketService.resultObservables.has(participation.id!)).toBeTrue();

        expect(participationWebsocketService.participationObservable).toBe(undefined);
    });

    it('should setup a result subscriptions with the websocket service on subscribeForLatestResult for students', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(subscribeSpy).toHaveBeenCalledWith(participationPersonalResultTopic);
        expect(receiveStub).toHaveBeenCalledOnce();
        expect(receiveStub).toHaveBeenCalledWith(participationPersonalResultTopic);
        expect(unsubscribeSpy).not.toHaveBeenCalled();

        expect(participationWebsocketService.cachedParticipations.size).toBe(0);

        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).toBe(0);
        expect(participationWebsocketService.openPersonalWebsocketSubscription).toBe(participationPersonalResultTopic);

        expect(participationWebsocketService.resultObservables.size).toBe(1);
        expect(participationWebsocketService.resultObservables.has(participation.id!)).toBeTrue();

        expect(participationWebsocketService.participationObservable).toBe(undefined);
    });

    it('should emit rated result when received through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = jest.spyOn(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newRatedResult);
    });

    it('should emit unrated result received through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = jest.spyOn(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newUnratedResult);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newUnratedResult);
    });

    it('should also emit participation update with new result when new rated result arrives through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = jest.spyOn(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);
        const participationObservable = new BehaviorSubject(undefined);
        const participationSpy = jest.spyOn(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newRatedResult);
        expect(participationSpy).toHaveBeenCalledOnce();
        expect(participationSpy).toHaveBeenCalledWith({ ...participation, results: [...participation.results!, newRatedResult] });
        expect(participationWebsocketService.cachedParticipations.get(participation.id!)).toEqual({ ...participation, results: [...participation.results!, newRatedResult] });
    });

    it('should emit participation update with new result when unrated result arrives through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = jest.spyOn(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);
        const participationObservable = new BehaviorSubject(undefined);
        const participationSpy = jest.spyOn(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newUnratedResult);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newUnratedResult);
        expect(participationSpy).toHaveBeenCalledOnce();
        expect(participationSpy).toHaveBeenCalledWith({ ...participation, results: [...participation.results!, newUnratedResult] });
        expect(participationWebsocketService.cachedParticipations.get(participation.id!)).toEqual({
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
        const resultSpy = jest.spyOn(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);
        const participationObservable = new BehaviorSubject(undefined);
        const participationSpy = jest.spyOn(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(participationWebsocketService.cachedParticipations.size).toBe(2);
        expect(participationWebsocketService.cachedParticipations.get(participation.id!)).toEqual({ ...participation, results: [...participation.results!, newRatedResult] });
        expect(participationWebsocketService.cachedParticipations.get(participation2.id!)).toEqual(participation2);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newRatedResult);
        expect(participationSpy).toHaveBeenCalledOnce();
        expect(participationSpy).toHaveBeenCalledWith({ ...participation, results: [...participation.results!, newRatedResult] });
    });

    it('should attach the result to participation if the participation has undefined for results value', () => {
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        const { results, ...participationWithoutResult } = participation;

        participationWebsocketService.subscribeForLatestResultOfParticipation(participationWithoutResult.id!, true);
        participationWebsocketService.addParticipation(participationWithoutResult as Participation);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = jest.spyOn(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participationWithoutResult.id!, resultObservable);
        const participationObservable = new BehaviorSubject(undefined);
        const participationSpy = jest.spyOn(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(participationWebsocketService.cachedParticipations.size).toBe(1);
        expect(participationWebsocketService.cachedParticipations.get(participationWithoutResult.id!)).toEqual({ ...participationWithoutResult, results: [newRatedResult] });

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newRatedResult);
        expect(participationSpy).toHaveBeenCalledOnce();
        expect(participationSpy).toHaveBeenCalledWith({ ...participationWithoutResult, results: [newRatedResult] });
    });

    it('should reset the local cache', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation2.id!, false, participation2.exercise!.id);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.addParticipation(participation2);

        expect(participationWebsocketService.openPersonalWebsocketSubscription).toBe(participationPersonalResultTopic);
        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).toBe(1);

        participationWebsocketService.resetLocalCache();

        expect(participationWebsocketService.openPersonalWebsocketSubscription).toBe(undefined);
        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).toBe(0);
    });

    it('should return the cached participation after adding it', () => {
        expect(participationWebsocketService.getParticipationForExercise(participation.exercise!.id!, false)).toBe(undefined);

        participationWebsocketService.addParticipation(participation);

        expect(participationWebsocketService.getParticipationForExercise(participation.exercise!.id!, false)).toEqual(participation);
    });
});
