import { TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Subject, firstValueFrom, of, take, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';

import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { IrisAskUserHttpService } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisAskUserService } from 'app/iris/overview/ask-user/services/iris-ask-user.service';
import { IrisChatService, IrisRunInfo } from 'app/iris/overview/services/iris-chat.service';
import { PageActivityService } from 'app/foundation/service/page-activity.service';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';

describe('IrisAskUserService', () => {
    let service: IrisAskUserService;
    let runInfoSubject: BehaviorSubject<IrisRunInfo | undefined>;
    let latestEventSubject: Subject<IrisPipeEvent | undefined>;
    let stopTimerSubject: Subject<void>;
    let pageLeavingSubject: Subject<void>;
    let awaitingAnswerStub: ReturnType<typeof vi.fn>;
    let currentLatestEventStub: ReturnType<typeof vi.fn>;
    let askUserHttpService: {
        latestSubmissionHasPoints: ReturnType<typeof vi.fn>;
        startTimer: ReturnType<typeof vi.fn>;
        stopTimer: ReturnType<typeof vi.fn>;
        registerDefocusForCurrentSession: ReturnType<typeof vi.fn>;
        startQuiz: ReturnType<typeof vi.fn>;
        startInClassQuiz: ReturnType<typeof vi.fn>;
    };
    let clearChatStub: ReturnType<typeof vi.fn>;

    const exerciseId = 42;

    beforeEach(() => {
        runInfoSubject = new BehaviorSubject<IrisRunInfo | undefined>(undefined);
        latestEventSubject = new Subject<IrisPipeEvent | undefined>();
        stopTimerSubject = new Subject<void>();
        pageLeavingSubject = new Subject<void>();
        awaitingAnswerStub = vi.fn(() => false);
        currentLatestEventStub = vi.fn(() => latestEventSubject.asObservable());
        askUserHttpService = {
            latestSubmissionHasPoints: vi.fn(() => of(true)),
            startTimer: vi.fn(() => of(new HttpResponse({ body: { timerExpiresAt: dayjs().add(2, 'minutes'), timeLimit: 120 } }))),
            stopTimer: vi.fn(() => of(new HttpResponse<void>())),
            registerDefocusForCurrentSession: vi.fn(() => of(new HttpResponse<void>())),
            startQuiz: vi.fn(() => of(new HttpResponse<void>())),
            startInClassQuiz: vi.fn(() => of(new HttpResponse<void>())),
        };
        clearChatStub = vi.fn(() => Promise.resolve());

        TestBed.configureTestingModule({
            providers: [
                IrisAskUserService,
                { provide: IrisAskUserHttpService, useValue: askUserHttpService },
                {
                    provide: IrisChatService,
                    useValue: {
                        currentLatestEvent: currentLatestEventStub,
                        currentRunInfo: vi.fn(() => runInfoSubject.asObservable()),
                        awaitingAnswer: awaitingAnswerStub,
                        stopTimer$: stopTimerSubject,
                        clearChat: clearChatStub,
                    },
                },
                { provide: PageActivityService, useValue: { pageLeaving$: pageLeavingSubject } },
            ],
        });

        service = TestBed.inject(IrisAskUserService);
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = exerciseId;
        exercise.type = ExerciseType.PROGRAMMING;
        service.exercise.set(exercise);
        service.activate();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should reset local quiz state when the active run fails', async () => {
        service.setActiveQuizTypeForExercise(exerciseId, 'regular');
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        expect(service.quizActive()).toBe(true);
        expect(service.quizStarted()).toBe(true);
        expect(service.timerExpiresAt()?.isValid()).toBe(true);
        expect(service.timeLimit()).toBe(120);

        runInfoSubject.next({ runId: 'run-1', state: IrisRunState.FAILED });

        expect(service.quizActive()).toBe(false);
        expect(service.quizStarted()).toBe(false);
        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBeUndefined();
        expect(askUserHttpService.stopTimer).not.toHaveBeenCalled();
    });

    it('should clear the timer state when starting the timer fails', () => {
        askUserHttpService.startTimer.mockReturnValue(throwError(() => new Error('timer failed')));

        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        expect(service.quizActive()).toBe(true);
        expect(service.quizStarted()).toBe(true);
        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
    });

    it('should keep local timer state cleared when stopping the timer fails', () => {
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);
        expect(service.timerExpiresAt()?.isValid()).toBe(true);
        askUserHttpService.stopTimer.mockReturnValue(throwError(() => new Error('stop failed')));

        stopTimerSubject.next();

        expect(askUserHttpService.stopTimer).toHaveBeenCalledExactlyOnceWith(exerciseId);
        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
    });

    it('should keep the local quiz state reset when registering defocus fails', () => {
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);
        askUserHttpService.registerDefocusForCurrentSession.mockReturnValue(throwError(() => new Error('defocus failed')));

        pageLeavingSubject.next();

        expect(awaitingAnswerStub).toHaveBeenCalledOnce();
        expect(askUserHttpService.registerDefocusForCurrentSession).toHaveBeenCalledExactlyOnceWith(exerciseId);
        expect(service.quizActive()).toBe(false);
        expect(service.quizStarted()).toBe(false);
        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
    });

    it('should reset a pending quiz start when the active run fails before the first quiz event', async () => {
        service.setActiveQuizTypeForExercise(exerciseId, 'inClass');

        runInfoSubject.next({ runId: 'run-1', state: IrisRunState.FAILED });

        expect(service.quizActive()).toBe(false);
        expect(service.quizStarted()).toBe(false);
        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBeUndefined();
        expect(askUserHttpService.stopTimer).not.toHaveBeenCalled();
    });

    it('should request the Iris panel activation when the user starts a quiz', () => {
        expect(service.irisPanelActivationRequest()).toBeUndefined();

        latestEventSubject.next(IrisPipeEvent.USER_STARTS_QUIZ);

        expect(service.quizStarted()).toBe(true);
        expect(service.irisPanelActivationRequest()).toEqual({ sequence: 1, exerciseId });

        latestEventSubject.next(IrisPipeEvent.USER_STARTS_QUIZ);

        expect(service.irisPanelActivationRequest()).toEqual({ sequence: 2, exerciseId });
    });

    it('should reset a regular quiz when a build with points arrives during the quiz', async () => {
        service.setActiveQuizTypeForExercise(exerciseId, 'regular');
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        expect(service.quizActive()).toBe(true);
        expect(service.quizStarted()).toBe(true);
        expect(service.timerExpiresAt()?.isValid()).toBe(true);

        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);

        expect(service.quizActive()).toBe(false);
        expect(service.quizStarted()).toBe(false);
        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBeUndefined();
    });

    it('should keep in-class quiz state when a build with points arrives', async () => {
        service.setActiveQuizTypeForExercise(exerciseId, 'inClass');
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);

        expect(service.quizActive()).toBe(true);
        expect(service.quizStarted()).toBe(true);
        expect(service.timerExpiresAt()?.isValid()).toBe(true);
        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBe('inClass');
    });

    it('should clear the chat and mark regular ask-user mode active before starting the quiz', async () => {
        await expect(firstValueFrom(service.startQuiz(exerciseId))).resolves.toBeUndefined();

        expect(clearChatStub).toHaveBeenCalledOnce();
        expect(askUserHttpService.startQuiz).toHaveBeenCalledExactlyOnceWith(exerciseId);
        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBe('regular');
    });

    it('should clear the chat and mark in-class ask-user mode active before starting the quiz', async () => {
        await expect(firstValueFrom(service.startInClassQuiz(exerciseId))).resolves.toBeUndefined();

        expect(clearChatStub).toHaveBeenCalledOnce();
        expect(askUserHttpService.startInClassQuiz).toHaveBeenCalledExactlyOnceWith(exerciseId);
        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBe('inClass');
    });

    it('should clear the active quiz type if starting regular ask-user mode fails', async () => {
        askUserHttpService.startQuiz.mockReturnValue(throwError(() => new Error('boom')));

        await expect(firstValueFrom(service.startQuiz(exerciseId))).rejects.toThrow(IrisErrorMessageKey.START_ASK_USER_FAILED);

        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBeUndefined();
    });

    it('should clear the active quiz type if starting in-class ask-user mode fails', async () => {
        askUserHttpService.startInClassQuiz.mockReturnValue(throwError(() => new Error('boom')));

        await expect(firstValueFrom(service.startInClassQuiz(exerciseId))).rejects.toThrow(IrisErrorMessageKey.START_ASK_USER_FAILED);

        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBeUndefined();
    });

    it('should resolve latestSubmissionHasPoints from the server for the current exercise', () => {
        TestBed.tick();

        expect(service.latestSubmissionHasPoints()).toBe(true);
        expect(askUserHttpService.latestSubmissionHasPoints).toHaveBeenCalledExactlyOnceWith(exerciseId);
    });

    it('should resolve latestSubmissionHasPoints to false when no exercise is set', () => {
        service.exercise.set(undefined);
        TestBed.tick();

        expect(service.latestSubmissionHasPoints()).toBe(false);
    });

    it('should resolve the active quiz type and isAnyAskUserMode for the current exercise', () => {
        TestBed.tick();
        expect(service.activeQuizType()).toBeUndefined();
        expect(service.isAnyAskUserMode()).toBe(false);

        service.setActiveQuizTypeForExercise(exerciseId, 'regular');
        TestBed.tick();

        expect(service.activeQuizType()).toBe('regular');
        expect(service.isAnyAskUserMode()).toBe(true);
    });

    it('should resolve the active quiz type to undefined when no exercise is set', () => {
        service.exercise.set(undefined);
        TestBed.tick();

        expect(service.activeQuizType()).toBeUndefined();
        expect(service.isAnyAskUserMode()).toBe(false);
    });

    it('should not reload data when activate is called while already enabled', () => {
        expect(currentLatestEventStub).toHaveBeenCalledOnce();

        service.activate();

        expect(currentLatestEventStub).toHaveBeenCalledOnce();
    });

    it('should disable the feature and be a no-op when deactivating again', () => {
        expect(service.enabled()).toBe(true);

        service.deactivate();
        expect(service.enabled()).toBe(false);

        service.deactivate();
        expect(service.enabled()).toBe(false);
    });

    it('should not start the timer when there is no current programming exercise', () => {
        service.exercise.set(undefined);

        (service as any).startAskUserTimer();

        expect(askUserHttpService.startTimer).not.toHaveBeenCalled();
    });

    it('should not update the timer state when the server response has no expiry', () => {
        askUserHttpService.startTimer.mockReturnValue(of(new HttpResponse({ body: { timeLimit: 60 } as any })));

        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
    });

    it('should be a no-op when a run fails while no quiz is active', () => {
        runInfoSubject.next({ runId: 'run-1', state: IrisRunState.FAILED });

        expect(service.quizActive()).toBe(false);
        expect(service.quizStarted()).toBe(false);
        expect(service.timerExpiresAt()).toBeUndefined();
    });

    it('should ignore pipe events when there is no current programming exercise', () => {
        service.exercise.set(undefined);

        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        expect(service.quizActive()).toBe(false);
        expect(askUserHttpService.startTimer).not.toHaveBeenCalled();
    });

    it('should restart the timer when the next question arrives', () => {
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);
        expect(askUserHttpService.startTimer).toHaveBeenCalledOnce();

        latestEventSubject.next(IrisPipeEvent.NEXT_QUESTION);

        expect(askUserHttpService.startTimer).toHaveBeenCalledTimes(2);
    });

    it('should reset the quiz state when the quiz finishes', () => {
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);
        expect(service.quizActive()).toBe(true);

        latestEventSubject.next(IrisPipeEvent.QUIZ_FINISHED);

        expect(service.quizActive()).toBe(false);
        expect(service.quizStarted()).toBe(false);
        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
    });

    it('should ignore unrecognized pipe events', () => {
        latestEventSubject.next(undefined);

        expect(service.quizActive()).toBe(false);
        expect(service.quizStarted()).toBe(false);
    });

    it('should not clear the active quiz type when the provided quiz type does not match the stored one', async () => {
        service.setActiveQuizTypeForExercise(exerciseId, 'regular');

        service.clearActiveQuizTypeForExercise(exerciseId, 'inClass');

        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBe('regular');
    });
});
