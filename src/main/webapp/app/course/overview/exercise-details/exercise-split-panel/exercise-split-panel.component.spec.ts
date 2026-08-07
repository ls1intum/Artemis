import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
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
import { IrisChatService, IrisRunInfo } from 'app/iris/overview/services/iris-chat.service';
import { ExerciseSplitPanelComponent } from 'app/course/overview/exercise-details/exercise-split-panel/exercise-split-panel.component';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared-ui/components/resizable-panels/resizable-panels.component';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { PageActivityService } from 'app/foundation/service/page-activity.service';
import { IrisAskUserHttpService } from 'app/iris/overview/ask-user/services/iris-ask-user-http.service';
import { IrisAskUserService } from 'app/iris/overview/ask-user/services/iris-ask-user.service';
import { IrisPipeEvent } from 'app/iris/shared/entities/iris-pipe-event.model';
import dayjs from 'dayjs/esm';
import { signal } from '@angular/core';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { LiveQuizParticipationStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { CourseInformationSharingConfiguration } from 'app/course/shared/entities/course.model';
import { TextEditorComponent } from 'app/text/overview/text-editor/text-editor.component';
import { CodeEditorStudentContainerComponent } from 'app/programming/overview/code-editor-student-container/code-editor-student-container.component';
import { ModelingSubmissionComponent } from 'app/modeling/overview/modeling-submission/modeling-submission.component';
import { FileUploadSubmissionComponent } from 'app/fileupload/overview/file-upload-submission/file-upload-submission.component';
import { QuizParticipationComponent } from 'app/quiz/overview/participation/quiz-participation.component';

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
    let irisAskUserService: IrisAskUserService;
    let runInfoSubject: BehaviorSubject<IrisRunInfo | undefined>;
    let latestEventSubject: Subject<IrisPipeEvent | undefined>;
    let stopTimerSubject: Subject<void>;
    let pageLeavingSubject: Subject<void>;
    let askUserHttpService: IrisAskUserHttpService;

    beforeEach(async () => {
        vi.stubGlobal('ResizeObserver', ResizeObserverMock);
        runInfoSubject = new BehaviorSubject<IrisRunInfo | undefined>(undefined);
        latestEventSubject = new Subject<IrisPipeEvent | undefined>();
        stopTimerSubject = new Subject<void>();
        pageLeavingSubject = new Subject<void>();
        await TestBed.configureTestingModule({
            imports: [ExerciseSplitPanelComponent],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                IrisAskUserService,
                {
                    provide: IrisChatService,
                    useValue: {
                        openChat: vi.fn(),
                        currentLatestEvent: vi.fn(() => latestEventSubject.asObservable()),
                        currentRunInfo: vi.fn(() => runInfoSubject.asObservable()),
                        awaitingAnswer: vi.fn(() => false),
                        stopTimer$: stopTimerSubject,
                    },
                },
                { provide: PageActivityService, useValue: { pageLeaving$: pageLeavingSubject.asObservable() } },
                {
                    provide: IrisAskUserHttpService,
                    useValue: {
                        latestSubmissionHasPoints: vi.fn(() => of(false)),
                        startTimer: vi.fn(() => of(new HttpResponse({ body: { timerExpiresAt: dayjs().add(30, 'seconds'), timeLimit: 30 } }))),
                        stopTimer: vi.fn(() => of(new HttpResponse<void>())),
                        registerDefocusForCurrentSession: vi.fn(() => of(new HttpResponse<void>())),
                    },
                },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: ActivatedRoute, useValue: { parent: {}, firstChild: undefined } },
                { provide: TranslateService, useClass: MockTranslateService },
                ChildrenOutletContexts,
            ],
        })
            .overrideComponent(ExerciseSplitPanelComponent, {
                set: {
                    template: `
                        <jhi-resizable-panels>
                            @if (showEditorPanel()) {
                                <ng-template jhiPanel [label]="editorLabelKey()">Editor</ng-template>
                            }
                            @if (exercise().type !== ExerciseType.QUIZ) {
                                <ng-template jhiPanel [label]="'problemStatement'">Problem Statement</ng-template>
                            }
                            @if (showIris()) {
                                <ng-template jhiPanel [label]="'artemisApp.courseOverview.exerciseDetails.iris'" [startsCollapsed]="irisPanelStartsCollapsed()">Iris</ng-template>
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
        irisAskUserService = TestBed.inject(IrisAskUserService);
        askUserHttpService = TestBed.inject(IrisAskUserHttpService);
        const exercise = { id: 1, type: ExerciseType.TEXT } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        irisAskUserService.exercise.set(exercise);
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('irisEnabled', true);
        fixture.detectChanges();
        irisAskUserService.activate();
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

    it('should activate the Iris panel when the user starts an ask-user quiz', () => {
        const exercise = { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        irisAskUserService.exercise.set(exercise);
        fixture.componentRef.setInput('studentParticipation', { id: 1 } as StudentParticipation);
        fixture.detectChanges();

        const resizablePanels = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;
        expect(resizablePanels.activeRightIndex()).toBe(0);

        resizablePanels.collapseRightPanel();
        fixture.detectChanges();

        latestEventSubject.next(IrisPipeEvent.USER_STARTS_QUIZ);
        fixture.detectChanges();

        expect((component as any).quizStarted()).toBe(true);
        expect(resizablePanels.isRightPanelCollapsed()).toBe(false);
        expect(resizablePanels.activeRightIndex()).toBe(1);
    });

    it('should start the embedded ask-user timer on the first question event', () => {
        const exercise = { id: 1, type: ExerciseType.PROGRAMMING } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        irisAskUserService.exercise.set(exercise);
        fixture.detectChanges();

        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        expect(askUserHttpService.startTimer).toHaveBeenCalledWith(1);
        expect((component as any).quizActive()).toBe(true);
        expect((component as any).timeLimit()).toBe(30);
    });

    it('should not stop the backend ask-user timer when the embedded timer expires locally', () => {
        const exercise = { id: 1, type: ExerciseType.PROGRAMMING } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        irisAskUserService.exercise.set(exercise);
        fixture.detectChanges();
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        (component as any).handleAskUserTimerExpired();

        expect(askUserHttpService.stopTimer).not.toHaveBeenCalled();
        expect((component as any).timerExpiresAt()).toBeUndefined();
        expect((component as any).timeLimit()).toBe(0);
    });

    it('should stop the backend ask-user timer when the current answer arrives', () => {
        const exercise = { id: 1, type: ExerciseType.PROGRAMMING } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        irisAskUserService.exercise.set(exercise);
        fixture.detectChanges();
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        stopTimerSubject.next();

        expect(askUserHttpService.stopTimer).toHaveBeenCalledWith(1);
        expect((component as any).timerExpiresAt()).toBeUndefined();
        expect((component as any).timeLimit()).toBe(0);
    });

    it('should register defocus for the embedded ask-user session when the page is left', () => {
        const exercise = { id: 1, type: ExerciseType.PROGRAMMING } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        irisAskUserService.exercise.set(exercise);
        fixture.detectChanges();
        latestEventSubject.next(IrisPipeEvent.FIRST_QUESTION);

        pageLeavingSubject.next();

        expect(askUserHttpService.registerDefocusForCurrentSession).toHaveBeenCalledWith(1);
        expect((component as any).quizActive()).toBe(false);
        expect((component as any).timerExpiresAt()).toBeUndefined();
        expect((component as any).timeLimit()).toBe(0);
    });

    it('should hide Iris for exercise types without a chat mode (e.g. modeling)', () => {
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.MODELING } as unknown as Exercise);
        fixture.detectChanges();

        expect(component.showIris()).toBe(false);
    });

    it('should hide Iris for exercises that belong to an exam exercise group', () => {
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, exerciseGroup: { id: 3 } } as unknown as Exercise);
        fixture.detectChanges();

        expect(component.showIris()).toBe(false);
    });

    it('should show discussion when the course has communication enabled', () => {
        fixture.componentRef.setInput('exercise', {
            id: 1,
            type: ExerciseType.TEXT,
            course: { courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING },
        } as unknown as Exercise);
        fixture.detectChanges();

        expect(component.showDiscussion()).toBe(true);
    });

    it('should hide discussion when the course has no communication configured', () => {
        fixture.componentRef.setInput('exercise', {
            id: 1,
            type: ExerciseType.TEXT,
            course: { courseInformationSharingConfiguration: CourseInformationSharingConfiguration.DISABLED },
        } as unknown as Exercise);
        fixture.detectChanges();

        expect(component.showDiscussion()).toBe(false);
    });

    it('should not navigate when the exercise has no id yet', () => {
        const navigateSpy = vi.mocked(TestBed.inject(Router).navigate);
        navigateSpy.mockClear();

        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as unknown as Exercise);
        fixture.detectChanges();

        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('should navigate to the text exercise participation route', () => {
        const navigateSpy = vi.mocked(TestBed.inject(Router).navigate);
        navigateSpy.mockClear();

        fixture.componentRef.setInput('studentParticipation', { id: 7 } as StudentParticipation);
        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledWith(['text-exercises', 1, 'participate', 7], expect.anything());
    });

    it('should navigate to the modeling exercise participation route', () => {
        const navigateSpy = vi.mocked(TestBed.inject(Router).navigate);
        navigateSpy.mockClear();

        fixture.componentRef.setInput('exercise', { id: 9, type: ExerciseType.MODELING } as unknown as Exercise);
        fixture.componentRef.setInput('studentParticipation', { id: 3 } as StudentParticipation);
        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledWith(['modeling-exercises', 9, 'participate', 3], expect.anything());
    });

    it('should navigate to the file upload exercise participation route', () => {
        const navigateSpy = vi.mocked(TestBed.inject(Router).navigate);
        navigateSpy.mockClear();

        fixture.componentRef.setInput('exercise', { id: 9, type: ExerciseType.FILE_UPLOAD } as unknown as Exercise);
        fixture.componentRef.setInput('studentParticipation', { id: 3 } as StudentParticipation);
        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledWith(['file-upload-exercises', 9, 'participate', 3], expect.anything());
    });

    it('should navigate to the live quiz route by default', () => {
        const navigateSpy = vi.mocked(TestBed.inject(Router).navigate);
        navigateSpy.mockClear();

        fixture.componentRef.setInput('exercise', { id: 9, type: ExerciseType.QUIZ } as unknown as Exercise);
        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledWith(['quiz-exercises', 9, 'live'], expect.anything());
    });

    it('should navigate to the practice quiz route when participationMode is practice', () => {
        const navigateSpy = vi.mocked(TestBed.inject(Router).navigate);
        navigateSpy.mockClear();

        fixture.componentRef.setInput('exercise', { id: 9, type: ExerciseType.QUIZ } as unknown as Exercise);
        fixture.componentRef.setInput('participationMode', 'practice');
        fixture.detectChanges();

        expect(navigateSpy).toHaveBeenCalledWith(['quiz-exercises', 9, 'practice'], expect.anything());
    });

    describe('editorLabelKey', () => {
        it.each([
            [ExerciseType.PROGRAMMING, 'artemisApp.courseOverview.exerciseDetails.codeEditor'],
            [ExerciseType.TEXT, 'artemisApp.courseOverview.exerciseDetails.textEditor'],
            [ExerciseType.MODELING, 'artemisApp.courseOverview.exerciseDetails.modelingEditor'],
            [ExerciseType.FILE_UPLOAD, 'artemisApp.courseOverview.exerciseDetails.fileUploadEditor'],
            [ExerciseType.QUIZ, 'artemisApp.courseOverview.exerciseDetails.quizEditor'],
        ])('should compute the editor label key for a given exercise type', (type, expected) => {
            fixture.componentRef.setInput('exercise', { id: 1, type } as unknown as Exercise);
            fixture.detectChanges();

            expect(component.editorLabelKey()).toBe(expected);
        });

        it('should fall back to the code editor label for an unrecognized exercise type', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: undefined } as unknown as Exercise);
            fixture.detectChanges();

            expect(component.editorLabelKey()).toBe('artemisApp.courseOverview.exerciseDetails.codeEditor');
        });
    });

    describe('showCodeEditor / usesRouterOutlet / showEditorPanel', () => {
        it('should show the code editor only for programming exercises with the online editor enabled', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true } as unknown as Exercise);
            fixture.detectChanges();
            expect(component.showCodeEditor()).toBe(true);

            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: false } as unknown as Exercise);
            fixture.detectChanges();
            expect(component.showCodeEditor()).toBe(false);

            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT } as unknown as Exercise);
            fixture.detectChanges();
            expect(component.showCodeEditor()).toBe(false);
        });

        it.each([ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.QUIZ])(
            'should use a router outlet for non-programming exercise types',
            (type) => {
                fixture.componentRef.setInput('exercise', { id: 1, type } as unknown as Exercise);
                fixture.detectChanges();

                expect(component.usesRouterOutlet()).toBe(true);
            },
        );

        it('should use a router outlet for a programming exercise with the online editor', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true } as unknown as Exercise);
            fixture.detectChanges();

            expect(component.usesRouterOutlet()).toBe(true);
        });

        it('should not use a router outlet for a programming exercise without the online editor', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: false } as unknown as Exercise);
            fixture.detectChanges();

            expect(component.usesRouterOutlet()).toBe(false);
        });

        it('should show the editor panel for quizzes even without a participation', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.QUIZ } as unknown as Exercise);
            fixture.detectChanges();

            expect(component.showEditorPanel()).toBe(true);
        });

        it('should hide the editor panel when there is no participation for a non-quiz exercise', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.MODELING } as unknown as Exercise);
            fixture.detectChanges();

            expect(component.showEditorPanel()).toBe(false);
        });

        it('should hide the programming editor panel without the online editor even with a participation', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: false } as unknown as Exercise);
            fixture.componentRef.setInput('studentParticipation', { id: 2 } as StudentParticipation);
            fixture.detectChanges();

            expect(component.showEditorPanel()).toBe(false);
        });
    });

    describe('showComplaintView / showRating', () => {
        it('should hide the complaint view and rating without a graded participation or result', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true } as unknown as Exercise);
            fixture.detectChanges();

            expect(component.showComplaintView()).toBe(false);
            expect(component.showRating()).toBe(false);
        });

        it.each([
            [AssessmentType.MANUAL, false, true],
            [AssessmentType.SEMI_AUTOMATIC, false, true],
            [AssessmentType.AUTOMATIC, false, false],
            [AssessmentType.AUTOMATIC, true, true],
        ])('should determine complaint view visibility from the assessment type and the automatic-complaints flag', (assessmentType, allowAutomatic, expected) => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true } as unknown as Exercise);
            fixture.componentRef.setInput('gradedStudentParticipation', { id: 2 } as StudentParticipation);
            fixture.componentRef.setInput('latestRatedResult', { assessmentType } as Result);
            fixture.componentRef.setInput('allowComplaintsForAutomaticAssessments', allowAutomatic);
            fixture.detectChanges();

            expect(component.showComplaintView()).toBe(expected);
        });

        it.each([
            [AssessmentType.MANUAL, true],
            [AssessmentType.SEMI_AUTOMATIC, true],
            [AssessmentType.AUTOMATIC, false],
        ])('should show the rating component only for manually assessed results', (assessmentType, expected) => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true } as unknown as Exercise);
            fixture.componentRef.setInput('gradedStudentParticipation', { id: 2 } as StudentParticipation);
            fixture.componentRef.setInput('latestRatedResult', { assessmentType } as Result);
            fixture.detectChanges();

            expect(component.showRating()).toBe(expected);
        });
    });

    describe('canSubmit', () => {
        it('should allow submitting a practice quiz once it has ended, even without a participation', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.QUIZ, quizEnded: true } as unknown as Exercise);
            fixture.componentRef.setInput('participationMode', 'practice');
            fixture.detectChanges();

            expect(component.canSubmit()).toBe(true);
        });

        it('should not allow submitting before any participation or quiz state exists', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT } as unknown as Exercise);
            fixture.detectChanges();

            expect(component.canSubmit()).toBe(false);
        });

        it('should allow submitting a quiz once a batch has started', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.QUIZ, quizBatches: [{ started: true }] } as unknown as Exercise);
            fixture.detectChanges();

            expect(component.canSubmit()).toBe(true);
        });

        it('should refuse to submit a non-quiz exercise without a participation, even once the quiz-started signal is latched', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT } as unknown as Exercise);
            fixture.detectChanges();
            const fakeQuiz = createFakeQuizComponent();
            component.onOutletActivate(fakeQuiz);
            fakeQuiz.quizStartedEvent.next();

            expect(component.canSubmit()).toBe(false);
        });

        it('should allow submitting a quiz once the student has started it locally', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.QUIZ } as unknown as Exercise);
            fixture.detectChanges();
            const fakeQuiz = createFakeQuizComponent();
            component.onOutletActivate(fakeQuiz);
            fakeQuiz.quizStartedEvent.next();

            expect(component.canSubmit()).toBe(true);
        });

        it('should allow submitting a programming exercise with the online editor and a participation', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true } as unknown as Exercise);
            fixture.componentRef.setInput('studentParticipation', { id: 2 } as StudentParticipation);
            fixture.detectChanges();

            expect(component.canSubmit()).toBe(true);
        });

        it('should refuse to submit a programming exercise without the online editor despite a participation', () => {
            fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: false } as unknown as Exercise);
            fixture.componentRef.setInput('studentParticipation', { id: 2 } as StudentParticipation);
            fixture.detectChanges();

            expect(component.canSubmit()).toBe(false);
        });

        it.each([ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD])('should allow submitting a non-programming exercise with a participation', (type) => {
            fixture.componentRef.setInput('exercise', { id: 1, type } as unknown as Exercise);
            fixture.componentRef.setInput('studentParticipation', { id: 2 } as StudentParticipation);
            fixture.detectChanges();

            expect(component.canSubmit()).toBe(true);
        });
    });

    describe('submitExercise', () => {
        let childrenOutletContexts: ChildrenOutletContexts;

        beforeEach(() => {
            childrenOutletContexts = TestBed.inject(ChildrenOutletContexts);
        });

        it('should do nothing when no outlet is activated', () => {
            expect(() => component.submitExercise()).not.toThrow();
        });

        it('should do nothing when the active outlet is not activated', () => {
            const fake = Object.create(TextEditorComponent.prototype);
            fake.submit = vi.fn();
            childrenOutletContexts.onChildOutletCreated('primary', { isActivated: false, component: fake } as any);

            component.submitExercise();

            expect(fake.submit).not.toHaveBeenCalled();
        });

        it('should submit a text editor component', () => {
            const fake = Object.create(TextEditorComponent.prototype);
            fake.submit = vi.fn();
            childrenOutletContexts.onChildOutletCreated('primary', { isActivated: true, component: fake } as any);

            component.submitExercise();

            expect(fake.submit).toHaveBeenCalledOnce();
        });

        it('should commit a code editor student container component', () => {
            const fake = Object.create(CodeEditorStudentContainerComponent.prototype);
            fake.commit = vi.fn();
            childrenOutletContexts.onChildOutletCreated('primary', { isActivated: true, component: fake } as any);

            component.submitExercise();

            expect(fake.commit).toHaveBeenCalledOnce();
        });

        it('should submit a modeling submission component', () => {
            const fake = Object.create(ModelingSubmissionComponent.prototype);
            fake.submit = vi.fn();
            childrenOutletContexts.onChildOutletCreated('primary', { isActivated: true, component: fake } as any);

            component.submitExercise();

            expect(fake.submit).toHaveBeenCalledOnce();
        });

        it('should submit a file upload submission component', () => {
            const fake = Object.create(FileUploadSubmissionComponent.prototype);
            fake.submitExercise = vi.fn().mockResolvedValue(undefined);
            childrenOutletContexts.onChildOutletCreated('primary', { isActivated: true, component: fake } as any);

            component.submitExercise();

            expect(fake.submitExercise).toHaveBeenCalledOnce();
        });

        it('should submit a quiz participation component', () => {
            const fake = Object.create(QuizParticipationComponent.prototype);
            fake.onSubmit = vi.fn();
            childrenOutletContexts.onChildOutletCreated('primary', { isActivated: true, component: fake } as any);

            component.submitExercise();

            expect(fake.onSubmit).toHaveBeenCalledOnce();
        });
    });

    describe('quiz outlet activation', () => {
        it('should forward quiz submitted events to the quizSubmitted output', () => {
            const fake = createFakeQuizComponent();
            component.onOutletActivate(fake);
            const submission = { id: 1 } as QuizSubmission;
            const emitted: QuizSubmission[] = [];
            component.quizSubmitted.subscribe((s) => emitted.push(s));

            fake.quizSubmittedEvent.next(submission);

            expect(emitted).toEqual([submission]);
        });

        it('should forward live quiz status changes to the liveQuizStatusChange output', () => {
            const fake = createFakeQuizComponent();
            component.onOutletActivate(fake);
            let emitted: LiveQuizParticipationStatus | undefined;
            component.liveQuizStatusChange.subscribe((status) => (emitted = status));

            fake.liveQuizStatusChange.next(LiveQuizParticipationStatus.SUBMITTED);

            expect(emitted).toBe(LiveQuizParticipationStatus.SUBMITTED);
        });

        it('should forward practice participation changes to the quizPracticeParticipationChanged output', () => {
            const fake = createFakeQuizComponent();
            component.onOutletActivate(fake);
            const participation = { id: 3 } as StudentParticipation;
            let emitted: StudentParticipation | undefined;
            component.quizPracticeParticipationChanged.subscribe((p) => (emitted = p));

            fake.practiceParticipationChanged.next(participation);

            expect(emitted).toBe(participation);
        });

        it('should forward live quiz result participations to the liveQuizResultParticipation output', () => {
            const fake = createFakeQuizComponent();
            component.onOutletActivate(fake);
            const participation = { id: 4 } as StudentParticipation;
            let emitted: StudentParticipation | undefined;
            component.liveQuizResultParticipation.subscribe((p) => (emitted = p));

            fake.liveQuizResultParticipation.next(participation);

            expect(emitted).toBe(participation);
        });

        it('should reflect the activated quiz component state via quizSubmitDisabled/quizSubmitTitle', () => {
            const fake = createFakeQuizComponent();
            (fake.isSubmitDisabled as any).set(true);
            (fake.submitTitleKey as any).set('artemisApp.quizExercise.submitted');

            component.onOutletActivate(fake);

            expect(component.quizSubmitDisabled()).toBe(true);
            expect(component.quizSubmitTitle()).toBe('artemisApp.quizExercise.submitted');
        });

        it('should reset quiz component state and stop forwarding events after outlet deactivation', () => {
            const fake = createFakeQuizComponent();
            (fake.isSubmitDisabled as any).set(true);
            component.onOutletActivate(fake);

            component.onOutletDeactivate();

            expect(component.quizSubmitDisabled()).toBe(false);
            expect(component.quizSubmitTitle()).toBe('entity.action.submit');
            const emitted: QuizSubmission[] = [];
            component.quizSubmitted.subscribe((s) => emitted.push(s));
            fake.quizSubmittedEvent.next({ id: 9 } as QuizSubmission);
            expect(emitted).toEqual([]);
        });

        it('should restart an active practice quiz and report success', () => {
            const fake = createFakeQuizComponent();
            component.onOutletActivate(fake);

            expect(component.restartPractice()).toBe(true);
            expect(fake.restartPractice).toHaveBeenCalledOnce();
        });

        it('should report failure when restarting a quiz that is not in practice mode', () => {
            const fake = createFakeQuizComponent();
            (fake.mode as any).set('live');
            component.onOutletActivate(fake);

            expect(component.restartPractice()).toBe(false);
            expect(fake.restartPractice).not.toHaveBeenCalled();
        });

        it('should report failure when restarting practice without an active quiz component', () => {
            expect(component.restartPractice()).toBe(false);
        });
    });
});

/**
 * Creates a stand-in for {@link QuizParticipationComponent} that satisfies the `instanceof` check in
 * `onOutletActivate`/`restartPractice` (via the real prototype) while exposing controllable Subjects/signals for
 * the small surface `ExerciseSplitPanelComponent` actually reads, instead of pulling in the full quiz component's
 * dependency tree.
 */
function createFakeQuizComponent(): QuizParticipationComponent {
    const fake = Object.create(QuizParticipationComponent.prototype);
    fake.quizStartedEvent = new Subject<void>();
    fake.quizSubmittedEvent = new Subject<QuizSubmission>();
    fake.liveQuizStatusChange = new Subject<LiveQuizParticipationStatus | undefined>();
    fake.practiceParticipationChanged = new Subject<StudentParticipation>();
    fake.liveQuizResultParticipation = new Subject<StudentParticipation>();
    fake.mode = signal('practice');
    fake.restartPractice = vi.fn();
    fake.isSubmitDisabled = signal(false);
    fake.submitTitleKey = signal('entity.action.submit');
    fake.liveHeaderInfo = signal(undefined);
    return fake as QuizParticipationComponent;
}
