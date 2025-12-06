import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject } from 'rxjs';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { MockParticipationService } from 'test/helpers/mocks/service/mock-participation.service';
import { Submission, getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

describe('ParticipationWebsocketService', () => {
    let websocketService: WebsocketService;
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
    const currentSubmission = { id: 1, participation } as Submission;
    const currentResult = { id: 10, submission: currentSubmission } as Result;
    currentSubmission.results = [currentResult];
    participation.submissions = [currentSubmission];
    const newRatedResult = {
        id: 11,
        rated: true,
        submission: { id: currentSubmission.id, participation } as Submission,
    } as Result;
    const newUnratedResult = {
        id: 12,
        rated: false,
        submission: { id: currentSubmission.id, participation } as Submission,
    } as Result;

    const participationPersonalResultTopic = `/user/topic/newResults`;
    const participationTopic = `/user/topic/exercise/${participation.exercise!.id}/participation`;

    const participation2 = { id: 2, exercise: { id: exerciseId2 } } as Participation;
    const currentResult2 = { id: 13, submission: { participation: participation2 } } as Result;
    const currentSubmission2 = { id: 2, participation: participation2, results: [currentResult2] } as Submission;
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
        expect(participationWebsocketService.openPersonalWebsocketSubscription).toBeUndefined();

        expect(participationWebsocketService.resultObservables.size).toBe(1);
        expect(participationWebsocketService.resultObservables.has(participation.id!)).toBeTrue();

        expect(participationWebsocketService.participationObservable).toBeUndefined();
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
        const participationObservable = new BehaviorSubject<Participation | undefined>(undefined);
        const participationSpy = jest.spyOn(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        // Emit new result from websocket
        receiveResultForParticipationSubject.next(newRatedResult);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newRatedResult);

        // Check participation emitted to subscribers
        expect(participationSpy).toHaveBeenCalledOnce();
        const updatedParticipation = participationSpy.mock.calls[0][0] as unknown as Participation;

        // submissions[].results must contain old + new
        const originalResults = getAllResultsOfAllSubmissions(participation.submissions!);
        const updatedResults = getAllResultsOfAllSubmissions(updatedParticipation.submissions!);
        expect(updatedResults).toEqual([...originalResults, newRatedResult]);

        // And the cached participation must be updated in the same way
        const cached = participationWebsocketService.cachedParticipations.get(participation.id!)!;
        const cachedResults = getAllResultsOfAllSubmissions(cached.submissions!);
        expect(cachedResults).toEqual([...originalResults, newRatedResult]);
    });

    it('should emit participation update with new result when unrated result arrives through websocket', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.subscribeForParticipationChanges();

        const resultObservable = new BehaviorSubject(undefined);
        const resultSpy = jest.spyOn(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participation.id!, resultObservable);

        const participationObservable = new BehaviorSubject<Participation | undefined>(undefined);
        const participationSpy = jest.spyOn(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        receiveResultForParticipationSubject.next(newUnratedResult);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newUnratedResult);

        expect(participationSpy).toHaveBeenCalledOnce();
        const updatedParticipation = participationSpy.mock.calls[0][0] as Participation;

        const originalIds = getAllResultsOfAllSubmissions(participation.submissions!).map((r) => r.id);
        const updatedIds = getAllResultsOfAllSubmissions(updatedParticipation.submissions!).map((r) => r.id);

        expect(updatedIds).toEqual([...originalIds, newUnratedResult.id]);

        const cached = participationWebsocketService.cachedParticipations.get(participation.id!)!;
        const cachedIds = getAllResultsOfAllSubmissions(cached.submissions!).map((r) => r.id);
        expect(cachedIds).toEqual([...originalIds, newUnratedResult.id]);
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

        const participationObservable = new BehaviorSubject<Participation | undefined>(undefined);
        const participationSpy = jest.spyOn(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        receiveResultForParticipationSubject.next(newRatedResult);

        expect(participationWebsocketService.cachedParticipations.size).toBe(2);

        const cached1 = participationWebsocketService.cachedParticipations.get(participation.id!)!;
        const cached2 = participationWebsocketService.cachedParticipations.get(participation2.id!)!;

        const originalIds1 = getAllResultsOfAllSubmissions(participation.submissions!).map((r) => r.id);
        const originalIds2 = getAllResultsOfAllSubmissions(participation2.submissions!).map((r) => r.id);

        const cachedIds1 = getAllResultsOfAllSubmissions(cached1.submissions!).map((r) => r.id);
        const cachedIds2 = getAllResultsOfAllSubmissions(cached2.submissions!).map((r) => r.id);

        expect(cachedIds1).toEqual([...originalIds1, newRatedResult.id]);
        expect(cachedIds2).toEqual(originalIds2);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newRatedResult);

        expect(participationSpy).toHaveBeenCalledOnce();
        const updatedParticipation = participationSpy.mock.calls[0][0] as Participation;
        const updatedIds = getAllResultsOfAllSubmissions(updatedParticipation.submissions!).map((r) => r.id);
        expect(updatedIds).toEqual([...originalIds1, newRatedResult.id]);
    });

    it('should attach the result to participation if the participation has undefined for results value', () => {
        const { submissions, ...participationWithoutResult } = participation;

        participationWebsocketService.subscribeForLatestResultOfParticipation(participationWithoutResult.id!, true);
        participationWebsocketService.addParticipation(participationWithoutResult as Participation);
        participationWebsocketService.subscribeForParticipationChanges();

        const resultObservable = new BehaviorSubject<undefined | Result>(undefined);
        const resultSpy = jest.spyOn(resultObservable, 'next');
        participationWebsocketService.resultObservables.set(participationWithoutResult.id!, resultObservable);

        const participationObservable = new BehaviorSubject<Participation | undefined>(undefined);
        const participationSpy = jest.spyOn(participationObservable, 'next');
        participationWebsocketService.participationObservable = participationObservable;

        receiveResultForParticipationSubject.next(newRatedResult);

        expect(participationWebsocketService.cachedParticipations.size).toBe(1);
        const cached = participationWebsocketService.cachedParticipations.get(participationWithoutResult.id!);
        expect(cached).toBeDefined();
        expect(cached!.id).toBe(participationWithoutResult.id);

        expect(resultSpy).toHaveBeenCalledOnce();
        expect(resultSpy).toHaveBeenCalledWith(newRatedResult);

        expect(participationSpy).toHaveBeenCalledOnce();
        const updatedParticipation = participationSpy.mock.calls[0][0] as Participation;
        expect(updatedParticipation.id).toBe(participationWithoutResult.id);
    });

    it('should reset the local cache', () => {
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation.id!, true);
        participationWebsocketService.subscribeForLatestResultOfParticipation(participation2.id!, false, participation2.exercise!.id);
        participationWebsocketService.addParticipation(participation);
        participationWebsocketService.addParticipation(participation2);

        expect(participationWebsocketService.openPersonalWebsocketSubscription).toBe(participationPersonalResultTopic);
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
