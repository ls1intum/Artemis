import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Signal, WritableSignal, computed, signal } from '@angular/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BehaviorSubject, Subject, of } from 'rxjs';

import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisAskUserHttpService } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisChatService, IrisRunInfo } from 'app/iris/overview/services/iris-chat.service';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { IrisStartQuizButtonComponent } from 'app/iris/overview/ask-user/start-quiz-button/start-quiz-button.component';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';
import { IrisAskUserQuizType, IrisAskUserService } from 'app/iris/overview/ask-user/services/iris-ask-user.service';

describe('IrisStartQuizButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisStartQuizButtonComponent>;
    let component: IrisStartQuizButtonComponent;
    let latestEventSubject: Subject<IrisPipeEvent | undefined>;
    let activeQuizType: WritableSignal<IrisAskUserQuizType | undefined>;
    let runInfoSubject: BehaviorSubject<IrisRunInfo | undefined>;
    let askUserService: {
        activeQuizType: WritableSignal<IrisAskUserQuizType | undefined>;
        latestSubmissionHasPoints: WritableSignal<boolean>;
        isAnyAskUserMode: Signal<boolean>;
        setActiveQuizTypeForExercise: ReturnType<typeof vi.fn>;
        clearActiveQuizTypeForExercise: ReturnType<typeof vi.fn>;
        startQuiz: ReturnType<typeof vi.fn>;
    };

    beforeEach(async () => {
        latestEventSubject = new Subject<IrisPipeEvent | undefined>();
        activeQuizType = signal<IrisAskUserQuizType | undefined>(undefined);
        runInfoSubject = new BehaviorSubject<IrisRunInfo | undefined>(undefined);
        askUserService = {
            activeQuizType,
            latestSubmissionHasPoints: signal(true),
            isAnyAskUserMode: computed(() => activeQuizType() !== undefined),
            setActiveQuizTypeForExercise: vi.fn((_exerciseId: number, quizType: IrisAskUserQuizType) => activeQuizType.set(quizType)),
            clearActiveQuizTypeForExercise: vi.fn(() => activeQuizType.set(undefined)),
            startQuiz: vi.fn(() => {
                activeQuizType.set('regular');
                return of(undefined);
            }),
        };

        await TestBed.configureTestingModule({
            imports: [IrisStartQuizButtonComponent],
            providers: [
                {
                    provide: IrisChatService,
                    useValue: {
                        currentLatestEvent: vi.fn(() => latestEventSubject.asObservable()),
                        currentRunInfo: vi.fn(() => runInfoSubject.asObservable()),
                    },
                },
                { provide: IrisAskUserService, useValue: askUserService },
                {
                    provide: IrisAskUserHttpService,
                    useValue: {
                        isQuizAlreadyDone: vi.fn(() => of(true)),
                        currentStartedQuizForExercise: vi.fn(() => of(false)),
                        currentStartedInClassQuizForExercise: vi.fn(() => of(false)),
                    },
                },
            ],
        })
            .overrideComponent(IrisStartQuizButtonComponent, {
                set: {
                    template: '',
                    imports: [],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisStartQuizButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('smallButtons', false);
        fixture.detectChanges();
    });

    it('should show currently quiz active while a new ask-user quiz is active even if the server still reports the previous quiz as completed', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        (component as any).startQuiz();
        latestEventSubject.next(IrisPipeEvent.USER_STARTS_QUIZ);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.currently');
    });

    it('should show currently while an in-class ask-user quiz is active', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        activeQuizType.set('inClass');
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.currently');
    });

    it('should keep the completed state while an in-class ask-user quiz is active if the regular quiz cannot be started', () => {
        activeQuizType.set('inClass');
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.finished');
    });

    it('should return to the start state after an in-class ask-user quiz finishes', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        activeQuizType.set('inClass');
        fixture.detectChanges();

        latestEventSubject.next(IrisPipeEvent.QUIZ_FINISHED);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.start');
    });

    it('should show quiz completed after askUser finishes', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();
        (component as any).startQuiz();

        latestEventSubject.next(IrisPipeEvent.QUIZ_FINISHED);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.finished');
    });

    it('should return to the start state after the active ask-user run fails', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();
        (component as any).startQuiz();
        fixture.detectChanges();

        runInfoSubject.next({ runId: 'run-1', state: IrisRunState.FAILED });
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.start');
    });

    it('should return to the start state after build with points arrives during a regular ask-user quiz', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();
        (component as any).startQuiz();
        fixture.detectChanges();

        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.start');
        expect(askUserService.clearActiveQuizTypeForExercise).toHaveBeenCalledWith(1, 'regular');
    });
});
