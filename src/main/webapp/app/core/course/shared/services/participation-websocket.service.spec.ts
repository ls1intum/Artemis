import { TestBed } from '@angular/core/testing';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { BehaviorSubject, Subject } from 'rxjs';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { MockParticipationService } from 'test/helpers/mocks/service/mock-participation.service';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';

describe('ParticipationWebsocketService', () => {
    let websocketService: WebsocketService;
    let receiveParticipationSubject: Subject<Participation>;
    let receiveParticipation2Subject: Subject<Participation>;
    let receiveResultForParticipationSubject: Subject<Result>;
    let receiveResultForParticipation2Subject: Subject<Result>;
    let subscribeSpy: jest.SpyInstance;
    let participationWebsocketService: ParticipationWebsocketService;

    const exercise1 = new ProgrammingExercise(undefined, undefined);
    exercise1.id = 20;
    const exercise2 = new ProgrammingExercise(undefined, undefined);
    exercise2.id = 40;

    const participation: Participation = { id: 1, exercise: exercise1 };
    const currentResult: Result = { id: 10 };
    const currentSubmission: Submission = { id: 1, participation, results: [currentResult] };
    currentResult.submission = currentSubmission;
    participation.submissions = [currentSubmission];
    const newRatedResult: Result = { id: 11, rated: true, submission: currentSubmission };
    const newUnratedResult: Result = { id: 12, rated: false, submission: currentSubmission };

    const participationPersonalResultTopic = `/user/topic/newResults`;
    const participationTopic = `/user/topic/exercise/${participation.exercise!.id}/participation`;

    const participation2: Participation = { id: 2, exercise: exercise2 };
    const currentResult2: Result = { id: 13, submission: { participation: participation2 } };
    const currentSubmission2: Submission = { id: 2, participation: participation2, results: [currentResult2] };
    participation2.submissions = [currentSubmission2];

    const participationInstructorResultTopic = `/topic/exercise/${participation2.exercise!.id}/newResults`;
    const participation2Topic = `/user/topic/exercise/${participation2.exercise!.id}/participation`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: ParticipationService, useClass: MockParticipationService },
            ],
        })
            .compileComponents()
            .then(() => {
                participationWebsocketService = TestBed.inject(ParticipationWebsocketService);
                websocketService = TestBed.inject(WebsocketService);

                subscribeSpy = jest.spyOn(websocketService, 'subscribe');

                receiveResultForParticipationSubject = new Subject();
                receiveResultForParticipation2Subject = new Subject();
                receiveParticipationSubject = new Subject();
                receiveParticipation2Subject = new Subject();
                subscribeSpy.mockImplementation((arg1) => {
                    switch (arg1) {
                        case participationPersonalResultTopic:
                            return receiveResultForParticipationSubject.asObservable();
                        case participationInstructorResultTopic:
                            return receiveResultForParticipation2Subject.asObservable();
                        case participationTopic:
                            return receiveParticipationSubject.asObservable();
                        case participation2Topic:
                            return receiveParticipation2Subject.asObservable();
                    }
                    return new Subject().asObservable();
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should setup a result subscriptions with the websocket service on subscribeForLatestResult for instructors', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, false, exercise2.id);
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(subscribeSpy).toHaveBeenCalledWith(participationInstructorResultTopic);

        expect(participationWebsocketService.cachedParticipations.size).toBe(0);

        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).toBe(1);
        expect(participationWebsocketService.openPersonalWebsocketSubscription).toBeUndefined();

        expect(participationWebsocketService.resultObservables.size).toBe(1);
        expect(participationWebsocketService.resultObservables.has(participation.id!)).toBeTrue();

        expect(participationWebsocketService.participationObservable).toBeUndefined();
    });

    it('should setup a result subscriptions with the websocket service on subscribeForLatestResult for students', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(subscribeSpy).toHaveBeenCalledWith(participationPersonalResultTopic);

        expect(participationWebsocketService.cachedParticipations.size).toBe(0);

        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).toBe(0);
        expect(participationWebsocketService.openPersonalWebsocketSubscription).toBeDefined();

        expect(participationWebsocketService.resultObservables.size).toBe(1);
        expect(participationWebsocketService.resultObservables.has(participation.id!)).toBeTrue();

        expect(participationWebsocketService.participationObservable).toBeUndefined();
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
        expect(participationSpy).toHaveBeenCalledWith(participation);
        expect(participationWebsocketService.cachedParticipations.get(participation.id!)).toEqual(participation);
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
        expect(participationSpy).toHaveBeenCalledWith(participation);
        expect(participationWebsocketService.cachedParticipations.get(participation.id!)).toEqual(participation);
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
        expect(participationWebsocketService.cachedParticipations.get(participation.id!)).toEqual(participation);
        expect(participationWebsocketService.cachedParticipations.get(participation2.id!)).toEqual(participation2);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newRatedResult);
        expect(participationSpy).toHaveBeenCalledOnce();
        expect(participationSpy).toHaveBeenCalledWith(participation);
    });

    it('should attach the result to participation if the participation has undefined for results value', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.addParticipation(participation as Participation);
        participationWebsocketService.subscribeForParticipationChanges();
        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = jest.spyOn(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);
        const participationObservable = new BehaviorSubject(undefined);
        const participationSpy = jest.spyOn(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(participationWebsocketService.cachedParticipations.size).toBe(1);
        expect(participationWebsocketService.cachedParticipations.get(participation.id!)).toEqual(participation);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newRatedResult);
        expect(participationSpy).toHaveBeenCalledOnce();
        expect(participationSpy).toHaveBeenCalledWith(participation);
    });

    it('should reset the local cache', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation2.id!, false, participation2.exercise!.id);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.addParticipation(participation2);

        expect(participationWebsocketService.openPersonalWebsocketSubscription).toBeDefined();
        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).toBe(1);

        participationWebsocketService.resetLocalCache();

        expect(participationWebsocketService.openPersonalWebsocketSubscription).toBeUndefined();
        expect(participationWebsocketService.openResultWebsocketSubscriptions.size).toBe(0);
    });

    it('should return the cached participation after adding it', () => {
        expect(participationWebsocketService.getParticipationsForExercise(participation.exercise!.id!)).toBeEmpty();

        participationWebsocketService.addParticipation(participation);

        expect(participationWebsocketService.getParticipationsForExercise(participation.exercise!.id!)).toEqual([participation]);
    });
});
