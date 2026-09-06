import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, ChildrenOutletContexts, Router, RouterOutlet } from '@angular/router';
import { Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { QuizParticipationBase } from 'app/quiz/overview/participation/quiz-participation.base';
import { LiveQuizParticipationStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { User } from 'app/account/user/user.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { ExerciseSplitPanelComponent } from 'app/course/overview/exercise-details/exercise-split-panel/exercise-split-panel.component';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared-ui/components/resizable-panels/resizable-panels.component';

class ResizeObserverMock {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

/**
 * Stands in for the concrete QuizParticipationComponent, which the split panel must recognise through
 * the {@link QuizParticipationBase} contract alone — it deliberately has no static import of it.
 */
class QuizParticipationStub extends QuizParticipationBase {
    readonly isSubmitDisabled = signal(false);
    readonly submitTitleKey = signal('entity.action.submit');
    readonly liveHeaderInfo = signal(undefined);
    readonly mode = signal('practice');
    readonly restartPractice = vi.fn();
    readonly quizStartedEvent = new Subject<void>();
    readonly quizSubmittedEvent = new Subject<QuizSubmission>();
    readonly liveQuizStatusChange = new Subject<LiveQuizParticipationStatus | undefined>();
    readonly practiceParticipationChanged = new Subject<StudentParticipation>();
    readonly liveQuizResultParticipation = new Subject<StudentParticipation>();
}

/** Attaches a stub component to the split panel's primary child outlet. */
function activatePrimaryOutletWith(component: unknown): void {
    const context = TestBed.inject(ChildrenOutletContexts).getOrCreateContext('primary');
    context.outlet = { isActivated: true, component } as unknown as RouterOutlet;
}

describe('ExerciseSplitPanelComponent', () => {
    let fixture: ComponentFixture<ExerciseSplitPanelComponent>;
    let component: ExerciseSplitPanelComponent;
    let accountService: MockAccountService;

    beforeEach(async () => {
        vi.stubGlobal('ResizeObserver', ResizeObserverMock);
        await TestBed.configureTestingModule({
            imports: [ExerciseSplitPanelComponent],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: IrisChatService, useValue: { openChat: vi.fn() } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: ActivatedRoute, useValue: { parent: {}, firstChild: undefined } },
                { provide: TranslateService, useClass: MockTranslateService },
                ChildrenOutletContexts,
            ],
        })
            .overrideComponent(ExerciseSplitPanelComponent, {
                set: {
                    template: `
                        <jhi-resizable-panels [flushLeftPanel]="exercise().type === ExerciseType.MODELING && showEditorPanel()">
                            @if (showEditorPanel()) {
                                <ng-template jhiPanel [label]="editorLabelKey()">Editor</ng-template>
                            }
                            @if (exercise().type !== ExerciseType.QUIZ) {
                                <ng-template jhiPanel [label]="'problemStatement'">Problem Statement</ng-template>
                            }
                            @if (showIris()) {
                                <ng-template jhiPanel [label]="'iris'" [startsCollapsed]="irisPanelStartsCollapsed()">Iris</ng-template>
                            }
                        </jhi-resizable-panels>
                    `,
                    imports: [ResizablePanelsComponent, PanelDirective],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseSplitPanelComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService) as unknown as MockAccountService;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT } as Exercise);
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('irisEnabled', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('should start the Iris panel collapsed for users who opted out of AI', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);

        expect(component.irisPanelStartsCollapsed()).toBe(true);
    });

    it('should not start the Iris panel collapsed for users who accepted AI', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);

        expect(component.irisPanelStartsCollapsed()).toBe(false);
    });

    it('should not start the Iris panel collapsed before the user made an AI selection', () => {
        accountService.userIdentity.set({ selectedLLMUsage: undefined } as User);

        expect(component.irisPanelStartsCollapsed()).toBe(false);
    });

    it('should make the modeling editor panel full bleed', () => {
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.MODELING } as Exercise);
        fixture.componentRef.setInput('studentParticipation', { id: 5 } as StudentParticipation);
        fixture.detectChanges();

        const panels = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;
        expect(panels.flushLeftPanel()).toBe(true);
    });

    it('should not make the left panel full bleed while the modeling exercise has no editor panel, so the problem statement keeps its padding', () => {
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.MODELING } as Exercise);
        fixture.componentRef.setInput('studentParticipation', undefined);
        fixture.detectChanges();

        const panels = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;
        expect(component.showEditorPanel()).toBe(false);
        expect(panels.flushLeftPanel()).toBe(false);
    });

    it('navigates only when the target route identity changes, not when the participation object is replaced (prevents the navigate-thrash loop on incoming results, #12976)', () => {
        const navigateSpy = vi.mocked(TestBed.inject(Router).navigate);

        // Programming exercise with the online editor: navigating to the code editor is expected on the first run.
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true } as unknown as Exercise);
        fixture.componentRef.setInput('studentParticipation', { id: 5 } as StudentParticipation);
        fixture.detectChanges();
        expect(navigateSpy).toHaveBeenCalledWith(['programming-exercises', 1, 'code-editor', 5], expect.anything());

        navigateSpy.mockClear();

        // An incoming result replaces the participation object but keeps its id. This must NOT re-navigate — otherwise
        // navigation thrashes and re-creates the code-editor subtree in a loop, flooding the server with requests.
        fixture.componentRef.setInput('studentParticipation', { id: 5, submissions: [{ id: 9 }] } as StudentParticipation);
        fixture.detectChanges();
        expect(navigateSpy).not.toHaveBeenCalled();

        // A genuine switch to a different participation still navigates.
        fixture.componentRef.setInput('studentParticipation', { id: 6 } as StudentParticipation);
        fixture.detectChanges();
        expect(navigateSpy).toHaveBeenCalledWith(['programming-exercises', 1, 'code-editor', 6], expect.anything());
    });

    it('should keep the problem statement open for users who opted out of AI when an editor panel is shown', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);
        fixture.componentRef.setInput('studentParticipation', { id: 1 } as StudentParticipation);
        fixture.detectChanges();

        const resizablePanels = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;

        expect(component.irisPanelStartsCollapsed()).toBe(false);
        expect(resizablePanels.isRightPanelCollapsed()).toBe(false);
        expect(resizablePanels.activeRightIndex()).toBe(0);
        expect(fixture.nativeElement.querySelector('.collapsed-right-panel')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('Problem Statement');
    });

    describe('submit dispatch', () => {
        it('should delegate to the participation component activated in the primary outlet', () => {
            const submitExercise = vi.fn();
            activatePrimaryOutletWith({ submitExercise });

            component.submitExercise();

            expect(submitExercise).toHaveBeenCalledOnce();
        });

        it('should do nothing when the activated component does not participate in submission', () => {
            // Guards the ExerciseSubmission contract: every child route currently resolves to a component
            // that implements it, so a future read-only child must stay a no-op rather than throw.
            activatePrimaryOutletWith({});

            expect(() => component.submitExercise()).not.toThrow();
        });

        it('should do nothing when no child route is activated', () => {
            expect(() => component.submitExercise()).not.toThrow();
        });
    });

    describe('quiz participation contract', () => {
        it('should recognise a quiz component through QuizParticipationBase and follow its lifecycle events', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.QUIZ } as Exercise);
            fixture.detectChanges();
            const quizComponent = new QuizParticipationStub();

            component.onOutletActivate(quizComponent);

            // Before the batch starts, a quiz is not submittable.
            expect(component.canSubmit()).toBe(false);

            quizComponent.quizStartedEvent.next();
            expect(component.canSubmit()).toBe(true);

            const submission = { id: 7 } as QuizSubmission;
            const submitted: QuizSubmission[] = [];
            component.quizSubmitted.subscribe((value: QuizSubmission) => submitted.push(value));
            quizComponent.quizSubmittedEvent.next(submission);
            expect(submitted).toEqual([submission]);

            // Practice restart is delegated to the quiz component rather than handled locally.
            expect(component.restartPractice()).toBe(true);
            expect(quizComponent.restartPractice).toHaveBeenCalledOnce();
        });

        it('should ignore an activated component that does not implement QuizParticipationBase', () => {
            component.onOutletActivate({ quizStartedEvent: new Subject<void>() });

            expect(component.restartPractice()).toBe(false);
        });
    });
    it('should withdraw submit while the routed participation surface is read-only', () => {
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.MODELING } as Exercise);
        fixture.componentRef.setInput('studentParticipation', { id: 5 } as StudentParticipation);
        fixture.detectChanges();

        expect(component.canSubmit()).toBe(true);

        const editable = signal(true);
        component.onOutletActivate({ submitExercise: () => {}, canSubmitExercise: editable });
        expect(component.canSubmit()).toBe(true);

        editable.set(false);
        expect(component.canSubmit()).toBe(false);

        component.onOutletDeactivate();
        component.onOutletActivate({ submitExercise: () => {} });
        expect(component.canSubmit()).toBe(true);
    });
});
