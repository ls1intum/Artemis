import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BehaviorSubject, Subject, of } from 'rxjs';
import dayjs from 'dayjs/esm';

import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisAskUserHttpService, IrisAskUserQuizType } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisAssessmentReviewHttpService } from 'app/iris/overview/ask-user/services/iris-assessment-review-http.service';
import { IrisChatService, IrisRunInfo } from 'app/iris/overview/services/iris-chat.service';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { IrisStartInClassQuizButtonComponent } from 'app/iris/overview/ask-user/start-in-class-quiz-button/start-in-class-quiz-button.component';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';

describe('IrisStartInClassQuizButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisStartInClassQuizButtonComponent>;
    let component: IrisStartInClassQuizButtonComponent;
    let latestEventSubject: Subject<IrisPipeEvent | undefined>;
    let activeQuizTypeSubject: BehaviorSubject<IrisAskUserQuizType | undefined>;
    let runInfoSubject: BehaviorSubject<IrisRunInfo | undefined>;
    let latestSubmissionHasPoints = false;
    let quizAlreadyDone = false;

    beforeEach(async () => {
        latestEventSubject = new Subject<IrisPipeEvent | undefined>();
        activeQuizTypeSubject = new BehaviorSubject<IrisAskUserQuizType | undefined>(undefined);
        runInfoSubject = new BehaviorSubject<IrisRunInfo | undefined>(undefined);
        latestSubmissionHasPoints = false;
        quizAlreadyDone = false;

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
                {
                    provide: IrisAskUserHttpService,
                    useValue: {
                        startInClassQuiz: vi.fn(() => of(undefined)),
                        latestSubmissionHasPoints: vi.fn(() => of(latestSubmissionHasPoints)),
                        isQuizAlreadyDone: vi.fn(() => of(quizAlreadyDone)),
                        currentStartedQuizForExercise: vi.fn(() => of(false)),
                        currentStartedInClassQuizForExercise: vi.fn(() => of(false)),
                        activeQuizTypeForExercise: vi.fn(() => activeQuizTypeSubject.asObservable()),
                        setActiveQuizTypeForExercise: vi.fn((_exerciseId: number, quizType: IrisAskUserQuizType) => activeQuizTypeSubject.next(quizType)),
                        clearActiveQuizTypeForExercise: vi.fn(() => activeQuizTypeSubject.next(undefined)),
                    },
                },
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
                set: {
                    template: '',
                    imports: [],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisStartInClassQuizButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('smallButtons', false);
        fixture.detectChanges();
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
        activeQuizTypeSubject.next('regular');
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.currently');
    });

    it('should keep the no-submission state while a regular ask-user quiz is active if the in-class quiz cannot be started', () => {
        activeQuizTypeSubject.next('regular');
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.askUser.noSubmission');
    });

    it('should return to the start state after a regular ask-user quiz finishes', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        activeQuizTypeSubject.next('regular');
        fixture.detectChanges();

        latestEventSubject.next(IrisPipeEvent.QUIZ_FINISHED);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.iris.assessmentInClassQuiz.start');
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
});
