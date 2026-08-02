import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Subject, firstValueFrom, of, take } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import dayjs from 'dayjs/esm';

import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { IrisAskUserHttpService } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisAskUserService } from 'app/iris/overview/ask-user/services/iris-ask-user.service';
import { IrisChatService, IrisRunInfo } from 'app/iris/overview/services/iris-chat.service';
import { PageActivityService } from 'app/foundation/service/page-activity.service';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';

describe('IrisAskUserService', () => {
    setupTestBed({ zoneless: true });

    let service: IrisAskUserService;
    let runInfoSubject: BehaviorSubject<IrisRunInfo | undefined>;
    let latestEventSubject: Subject<IrisPipeEvent | undefined>;
    let stopTimerSubject: Subject<void>;
    let askUserHttpService: {
        latestSubmissionHasPoints: ReturnType<typeof vi.fn>;
        startTimer: ReturnType<typeof vi.fn>;
        stopTimer: ReturnType<typeof vi.fn>;
        registerDefocusForCurrentSession: ReturnType<typeof vi.fn>;
    };

    const exerciseId = 42;

    beforeEach(() => {
        runInfoSubject = new BehaviorSubject<IrisRunInfo | undefined>(undefined);
        latestEventSubject = new Subject<IrisPipeEvent | undefined>();
        stopTimerSubject = new Subject<void>();
        askUserHttpService = {
            latestSubmissionHasPoints: vi.fn(() => of(true)),
            startTimer: vi.fn(() => of(new HttpResponse({ body: { timerExpiresAt: dayjs().add(2, 'minutes'), timeLimit: 120 } }))),
            stopTimer: vi.fn(() => of(new HttpResponse<void>())),
            registerDefocusForCurrentSession: vi.fn(() => of(new HttpResponse<void>())),
        };

        TestBed.configureTestingModule({
            providers: [
                IrisAskUserService,
                { provide: IrisAskUserHttpService, useValue: askUserHttpService },
                {
                    provide: IrisChatService,
                    useValue: {
                        currentLatestEvent: vi.fn(() => latestEventSubject.asObservable()),
                        currentRunInfo: vi.fn(() => runInfoSubject.asObservable()),
                        stopTimer$: stopTimerSubject,
                    },
                },
                { provide: PageActivityService, useValue: { pageLeaving$: new Subject<void>() } },
            ],
        });

        service = TestBed.inject(IrisAskUserService);
        const exercise = new ProgrammingExercise();
        exercise.id = exerciseId;
        exercise.type = ExerciseType.PROGRAMMING;
        service.exercise.set(exercise);
        service.activate();
    });

    it('should reset local quiz state when the active run fails', async () => {
        service.setActiveQuizTypeForExercise(exerciseId, 'regular');
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        expect(service.quizActive()).toBeTrue();
        expect(service.quizStarted()).toBeTrue();
        expect(service.timerExpiresAt()).toBeDefined();
        expect(service.timeLimit()).toBe(120);

        runInfoSubject.next({ runId: 'run-1', state: IrisRunState.FAILED });

        expect(service.quizActive()).toBeFalse();
        expect(service.quizStarted()).toBeFalse();
        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBeUndefined();
        expect(askUserHttpService.stopTimer).not.toHaveBeenCalled();
    });

    it('should reset a pending quiz start when the active run fails before the first quiz event', async () => {
        service.setActiveQuizTypeForExercise(exerciseId, 'inClass');

        runInfoSubject.next({ runId: 'run-1', state: IrisRunState.FAILED });

        expect(service.quizActive()).toBeFalse();
        expect(service.quizStarted()).toBeFalse();
        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBeUndefined();
        expect(askUserHttpService.stopTimer).not.toHaveBeenCalled();
    });

    it('should reset a regular quiz when a build with points arrives during the quiz', async () => {
        service.setActiveQuizTypeForExercise(exerciseId, 'regular');
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        expect(service.quizActive()).toBeTrue();
        expect(service.quizStarted()).toBeTrue();
        expect(service.timerExpiresAt()).toBeDefined();

        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);

        expect(service.quizActive()).toBeFalse();
        expect(service.quizStarted()).toBeFalse();
        expect(service.timerExpiresAt()).toBeUndefined();
        expect(service.timeLimit()).toBe(0);
        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBeUndefined();
    });

    it('should keep in-class quiz state when a build with points arrives', async () => {
        service.setActiveQuizTypeForExercise(exerciseId, 'inClass');
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);

        expect(service.quizActive()).toBeTrue();
        expect(service.quizStarted()).toBeTrue();
        expect(service.timerExpiresAt()).toBeDefined();
        await expect(firstValueFrom(service.activeQuizTypeForExercise(exerciseId).pipe(take(1)))).resolves.toBe('inClass');
    });
});
