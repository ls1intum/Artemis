import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of } from 'rxjs';

import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisAssessmentQuizService } from 'app/iris/overview/services/iris-assessment-quiz.service';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import { IrisStartPromptingButtonComponent } from 'app/iris/overview/understanding-assessment/start-prompting-button/start-prompting-button.component';

describe('IrisStartPromptingButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<IrisStartPromptingButtonComponent>;
    let component: IrisStartPromptingButtonComponent;
    let latestEventSubject: Subject<IrisPipeEvent | undefined>;

    beforeEach(async () => {
        latestEventSubject = new Subject<IrisPipeEvent | undefined>();

        await TestBed.configureTestingModule({
            imports: [IrisStartPromptingButtonComponent],
            providers: [
                {
                    provide: IrisChatService,
                    useValue: {
                        currentLatestEvent: vi.fn(() => latestEventSubject.asObservable()),
                        startPromptingMode: vi.fn(() => of(undefined)),
                    },
                },
                {
                    provide: IrisAssessmentQuizService,
                    useValue: {
                        latestSubmissionHasPoints: vi.fn(() => of(true)),
                        isQuizAlreadyDone: vi.fn(() => of(true)),
                        currentStartedInClassQuizForExercise: vi.fn(() => of(false)),
                    },
                },
            ],
        })
            .overrideComponent(IrisStartPromptingButtonComponent, {
                set: {
                    template: '',
                    imports: [],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisStartPromptingButtonComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('smallButtons', false);
        fixture.detectChanges();
    });

    it('should show currently prompting while a new prompting quiz is active even if the server still reports the previous quiz as completed', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();

        (component as any).startPromptingMode();
        latestEventSubject.next(IrisPipeEvent.USER_INITIATES_PROMPTING);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.prompting.currently');
    });

    it('should show quiz completed after prompting finishes', () => {
        latestEventSubject.next(IrisPipeEvent.BUILD_WITH_POINTS);
        fixture.detectChanges();
        (component as any).startPromptingMode();

        latestEventSubject.next(IrisPipeEvent.PROMPTING_FINISHED);
        fixture.detectChanges();

        expect((component as any).buttonLabel()).toBe('artemisApp.exerciseActions.prompting.finished');
    });
});
