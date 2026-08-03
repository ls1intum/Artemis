import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Signal, WritableSignal, computed, signal } from '@angular/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';

import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisAskUserHttpService } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisChatService, IrisRunInfo } from 'app/iris/overview/services/iris-chat.service';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { IrisStartQuizButtonComponent } from 'app/iris/overview/ask-user/start-quiz-button/start-quiz-button.component';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';
import { IrisAskUserQuizType, IrisAskUserService } from 'app/iris/overview/ask-user/services/iris-ask-user.service';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AlertService } from 'app/foundation/service/alert.service';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';

describe('IrisStartQuizButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisStartQuizButtonComponent>;
    let component: IrisStartQuizButtonComponent;
    let latestEventSubject: Subject<IrisPipeEvent | undefined>;
    let activeQuizType: WritableSignal<IrisAskUserQuizType | undefined>;
    let runInfoSubject: BehaviorSubject<IrisRunInfo | undefined>;
    let alertService: AlertService;
    let askUserHttpService: {
        isQuizAlreadyDone: ReturnType<typeof vi.fn>;
        currentStartedQuizForExercise: ReturnType<typeof vi.fn>;
        currentStartedInClassQuizForExercise: ReturnType<typeof vi.fn>;
    };
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
        askUserHttpService = {
            isQuizAlreadyDone: vi.fn(() => of(true)),
            currentStartedQuizForExercise: vi.fn(() => of(false)),
            currentStartedInClassQuizForExercise: vi.fn(() => of(false)),
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
                MockProvider(AlertService),
                { provide: IrisAskUserHttpService, useValue: askUserHttpService },
            ],
        })
            .overrideComponent(IrisStartQuizButtonComponent, {
                remove: {
                    imports: [FeatureToggleDirective, ArtemisTranslatePipe, FaIconComponent],
                },
                add: {
                    imports: [MockDirective(FeatureToggleDirective), MockPipe(ArtemisTranslatePipe, (key: string) => key), MockComponent(FaIconComponent)],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisStartQuizButtonComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService);
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('smallButtons', false);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should start the quiz from the rendered button', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
        button.click();
        fixture.detectChanges();

        expect(askUserService.startQuiz).toHaveBeenCalledExactlyOnceWith(1);
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

    it('should reset local state and show an alert when starting the quiz fails', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        askUserService.startQuiz.mockReturnValue(throwError(() => new Error('boom')));
        const alertSpy = vi.spyOn(alertService, 'error');
        fixture.detectChanges();

        const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
        button.click();
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.start');
        expect(askUserService.clearActiveQuizTypeForExercise).toHaveBeenCalledWith(1, 'regular');
        expect(alertSpy).toHaveBeenCalledExactlyOnceWith(IrisErrorMessageKey.START_ASK_USER_FAILED);
    });

    it('should keep the inactive state when initial started quiz checks fail', () => {
        fixture.destroy();
        activeQuizType.set(undefined);
        askUserService.setActiveQuizTypeForExercise.mockClear();
        askUserHttpService.currentStartedQuizForExercise.mockReturnValue(throwError(() => new Error('regular failed')));
        askUserHttpService.currentStartedInClassQuizForExercise.mockReturnValue(throwError(() => new Error('in-class failed')));

        fixture = TestBed.createComponent(IrisStartQuizButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('smallButtons', false);
        fixture.detectChanges();

        expect(activeQuizType()).toBeUndefined();
        expect(askUserService.setActiveQuizTypeForExercise).not.toHaveBeenCalled();
        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.finished');
    });
});
