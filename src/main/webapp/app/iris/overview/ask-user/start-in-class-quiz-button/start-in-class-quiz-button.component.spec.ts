import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Signal, WritableSignal, computed, signal } from '@angular/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import dayjs from 'dayjs/esm';

import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisAskUserHttpService } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisAskUserQuizType, IrisAskUserService } from 'app/iris/overview/ask-user/services/iris-ask-user.service';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { IrisChatService, IrisRunInfo } from 'app/iris/overview/services/iris-chat.service';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { IrisStartInClassQuizButtonComponent } from 'app/iris/overview/ask-user/start-in-class-quiz-button/start-in-class-quiz-button.component';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { QuizTimerBarComponent } from 'app/iris/overview/ask-user/quiz-timer-bar/quiz-timer-bar.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AlertService } from 'app/foundation/service/alert.service';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';

describe('IrisStartInClassQuizButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisStartInClassQuizButtonComponent>;
    let component: IrisStartInClassQuizButtonComponent;
    let latestEventSubject: Subject<IrisPipeEvent | undefined>;
    let activeQuizType: WritableSignal<IrisAskUserQuizType | undefined>;
    let runInfoSubject: BehaviorSubject<IrisRunInfo | undefined>;
    let askUserHttpService: {
        latestSubmissionHasPoints: ReturnType<typeof vi.fn>;
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
        startInClassQuiz: ReturnType<typeof vi.fn>;
    };
    let assessmentReviewService: IrisAssessmentReviewHttpService;
    let alertService: AlertService;
    let latestSubmissionHasPoints = false;
    let quizAlreadyDone = false;

    beforeEach(async () => {
        latestEventSubject = new Subject<IrisPipeEvent | undefined>();
        activeQuizType = signal<IrisAskUserQuizType | undefined>(undefined);
        runInfoSubject = new BehaviorSubject<IrisRunInfo | undefined>(undefined);
        latestSubmissionHasPoints = false;
        quizAlreadyDone = false;
        askUserService = {
            activeQuizType,
            latestSubmissionHasPoints: signal(latestSubmissionHasPoints),
            isAnyAskUserMode: computed(() => activeQuizType() !== undefined),
            setActiveQuizTypeForExercise: vi.fn((_exerciseId: number, quizType: IrisAskUserQuizType) => activeQuizType.set(quizType)),
            clearActiveQuizTypeForExercise: vi.fn(() => activeQuizType.set(undefined)),
            startInClassQuiz: vi.fn(() => {
                activeQuizType.set('inClass');
                return of(undefined);
            }),
        };
        askUserHttpService = {
            latestSubmissionHasPoints: vi.fn(() => of(latestSubmissionHasPoints)),
            isQuizAlreadyDone: vi.fn(() => of(quizAlreadyDone)),
            currentStartedQuizForExercise: vi.fn(() => of(false)),
            currentStartedInClassQuizForExercise: vi.fn(() => of(false)),
        };

        await TestBed.configureTestingModule({
            imports: [IrisStartInClassQuizButtonComponent],
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
                {
                    provide: IrisAssessmentReviewHttpService,
                    useValue: {
                        availableInClassQuizForExercise: vi.fn(() => of({ timerExpiresAt: dayjs().add(10, 'minutes'), timeLimit: 600 })),
                        clearActiveInClassQuiz: vi.fn(),
                    },
                },
            ],
        })
            .overrideComponent(IrisStartInClassQuizButtonComponent, {
                remove: {
                    imports: [FeatureToggleDirective, ArtemisTranslatePipe, QuizTimerBarComponent, FaIconComponent],
                },
                add: {
                    imports: [
                        MockDirective(FeatureToggleDirective),
                        MockPipe(ArtemisTranslatePipe, (key: string) => key),
                        MockComponent(QuizTimerBarComponent),
                        MockComponent(FaIconComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisStartInClassQuizButtonComponent);
        component = fixture.componentInstance;
        assessmentReviewService = TestBed.inject(IrisAssessmentReviewHttpService);
        alertService = TestBed.inject(AlertService);
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('smallButtons', false);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should become startable after build with points even if the server initially reports no submission with points', () => {
        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.noSubmission');

        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.iris.assessmentInClassQuiz.start');
    });

    it('should ignore build with points after the in-class quiz was already completed on the server', () => {
        quizAlreadyDone = true;
        fixture = TestBed.createComponent(IrisStartInClassQuizButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('smallButtons', false);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.finished');

        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.finished');
    });

    it('should show currently while a regular ask-user quiz is active', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        activeQuizType.set('regular');
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.currently');
    });

    it('should keep the no-submission state while a regular ask-user quiz is active if the in-class quiz cannot be started', () => {
        activeQuizType.set('regular');
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.noSubmission');
    });

    it('should return to the start state after a regular ask-user quiz finishes', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        activeQuizType.set('regular');
        fixture.detectChanges();

        latestEventSubject.next(IrisPipeEvent.QUIZ_FINISHED);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.iris.assessmentInClassQuiz.start');
    });

    it('should start the in-class quiz from the rendered button', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
        button.click();
        fixture.detectChanges();

        expect(askUserService.startInClassQuiz).toHaveBeenCalledExactlyOnceWith(1);
    });

    it('should show quiz completed after the in-class ask-user quiz finishes', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        (component as any).startInClassQuiz();
        latestEventSubject.next(IrisPipeEvent.QUIZ_FINISHED);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.finished');
    });

    it('should ignore build with points after the in-class quiz was completed in the current run', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        (component as any).startInClassQuiz();
        latestEventSubject.next(IrisPipeEvent.QUIZ_FINISHED);
        fixture.detectChanges();

        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.finished');
    });

    it('should return to the start state after the active in-class ask-user run fails', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        (component as any).startInClassQuiz();
        fixture.detectChanges();

        runInfoSubject.next({ runId: 'run-1', state: IrisRunState.FAILED });
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.iris.assessmentInClassQuiz.start');
    });

    it('should clear the active in-class quiz when the rendered timer expires', () => {
        const clearActiveInClassQuizSpy = vi.spyOn(assessmentReviewService, 'clearActiveInClassQuiz');

        (component as any).handleTimerExpired();

        expect(clearActiveInClassQuizSpy).toHaveBeenCalledExactlyOnceWith(1);
    });

    it('should reset local state and show an alert when starting the quiz fails', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        askUserService.startInClassQuiz.mockReturnValue(throwError(() => new Error('boom')));
        const alertSpy = vi.spyOn(alertService, 'error');
        fixture.detectChanges();

        const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
        button.click();
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.iris.assessmentInClassQuiz.start');
        expect(askUserService.clearActiveQuizTypeForExercise).toHaveBeenCalledWith(1, 'inClass');
        expect(alertSpy).toHaveBeenCalledExactlyOnceWith(IrisErrorMessageKey.START_ASK_USER_FAILED);
    });

    it('should keep the inactive state when initial started quiz checks fail', () => {
        fixture.destroy();
        activeQuizType.set(undefined);
        askUserService.setActiveQuizTypeForExercise.mockClear();
        askUserHttpService.currentStartedQuizForExercise.mockReturnValue(throwError(() => new Error('regular failed')));
        askUserHttpService.currentStartedInClassQuizForExercise.mockReturnValue(throwError(() => new Error('in-class failed')));

        fixture = TestBed.createComponent(IrisStartInClassQuizButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('smallButtons', false);
        fixture.detectChanges();

        expect(activeQuizType()).toBeUndefined();
        expect(askUserService.setActiveQuizTypeForExercise).not.toHaveBeenCalled();
        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.noSubmission');
    });

    it('should hide the in-class start option when loading the available quiz fails', () => {
        fixture.destroy();
        activeQuizType.set(undefined);
        vi.spyOn(assessmentReviewService, 'availableInClassQuizForExercise').mockReturnValue(throwError(() => new Error('available failed')));

        fixture = TestBed.createComponent(IrisStartInClassQuizButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('smallButtons', false);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.noSubmission');
    });
});
