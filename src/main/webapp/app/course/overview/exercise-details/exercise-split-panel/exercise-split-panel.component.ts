import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { ActivatedRoute, ChildrenOutletContexts, Router, RouterLink, RouterOutlet } from '@angular/router';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { faAlignLeft, faComment, faGear, faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { ProblemStatementComponent } from 'app/course/overview/exercise-details/problem-statement/problem-statement.component';
import { ExerciseSubmission } from 'app/exercise/shared/exercise-submission.interface';
import { QuizLiveHeaderInfo } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { LiveQuizParticipationStatus, QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { ParticipationMode } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/course/shared/entities/course.model';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared-ui/components/resizable-panels/resizable-panels.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ResetRepoButtonComponent } from 'app/course/overview/exercise-details/reset-repo-button/reset-repo-button.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/programming/shared/actions/example-solution-repo-download/programming-exercise-example-solution-repo-download.component';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';
import { LtiInitializerComponent } from 'app/course/overview/exercise-details/lti-initializer/lti-initializer.component';
import { PanelModule } from 'primeng/panel';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ExampleSolutionInfo } from 'app/exercise/services/exercise.service';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';

/**
 * Minimal interface for quiz participation components activated via the router outlet.
 * Avoids a static import of QuizParticipationComponent which would defeat lazy chunk splitting.
 */
interface QuizComponentRef {
    isSubmitDisabled: () => boolean;
    submitTitleKey: () => string;
    liveHeaderInfo: () => QuizLiveHeaderInfo | undefined;
    mode: () => string;
    restartPractice: () => void;
    quizStartedEvent: { subscribe(fn: () => void): { unsubscribe(): void } };
    quizSubmittedEvent: { subscribe(fn: (s: QuizSubmission) => void): { unsubscribe(): void } };
    liveQuizStatusChange: { subscribe(fn: (s: LiveQuizParticipationStatus | undefined) => void): { unsubscribe(): void } };
    practiceParticipationChanged: { subscribe(fn: (p: StudentParticipation) => void): { unsubscribe(): void } };
    liveQuizResultParticipation: { subscribe(fn: (p: StudentParticipation) => void): { unsubscribe(): void } };
}

function isQuizComponentRef(component: unknown): component is QuizComponentRef {
    return !!component && 'quizStartedEvent' in (component as object) && typeof (component as any).isSubmitDisabled === 'function';
}

@Component({
    selector: 'jhi-exercise-split-panel',
    templateUrl: './exercise-split-panel.component.html',
    imports: [
        RouterOutlet,
        RouterLink,
        ResizablePanelsComponent,
        PanelDirective,
        ProblemStatementComponent,
        IrisBaseChatbotComponent,
        IrisLogoComponent,
        TranslateDirective,
        ResetRepoButtonComponent,
        ComplaintsStudentViewComponent,
        RatingComponent,
        ModelingEditorComponent,
        ProgrammingExerciseExampleSolutionRepoDownloadComponent,
        CompetencyContributionComponent,
        LtiInitializerComponent,
        NgbTooltip,
        ArtemisTranslatePipe,
        DiscussionSectionComponent,
        PanelModule,
    ],
})
export class ExerciseSplitPanelComponent {
    private readonly chatService = inject(IrisChatService);
    private readonly accountService = inject(AccountService);
    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    private readonly childrenOutletContexts = inject(ChildrenOutletContexts);
    // Tracks whether a quiz batch is started / the quiz has ended, from the server-provided exercise data.
    // Updated via effect (safe for required inputs) rather than computed (would throw NG0950 during early init).
    private readonly _quizBatchStarted = signal(false);
    private readonly _quizEnded = signal(false);
    private readonly _quizHasStarted = signal(false);
    private readonly _quizComponent = signal<QuizComponentRef | undefined>(undefined);
    private quizStartedSubscription: { unsubscribe(): void } | undefined;
    private quizSubmittedSubscription: { unsubscribe(): void } | undefined;
    private liveQuizStatusSubscription: { unsubscribe(): void } | undefined;
    private quizPracticeParticipationSubscription: { unsubscribe(): void } | undefined;
    private liveQuizResultSubscription: { unsubscribe(): void } | undefined;

    readonly quizSubmitted = output<QuizSubmission>();
    readonly quizPracticeParticipationChanged = output<StudentParticipation>();
    readonly liveQuizResultParticipation = output<StudentParticipation>();
    readonly liveQuizStatusChange = output<LiveQuizParticipationStatus | undefined>();

    readonly quizSubmitDisabled = computed(() => this._quizComponent()?.isSubmitDisabled() ?? false);
    readonly quizSubmitTitle = computed(() => this._quizComponent()?.submitTitleKey() ?? 'entity.action.submit');
    readonly quizLiveHeaderInfo = computed(() => this._quizComponent()?.liveHeaderInfo());
    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly faGear = faGear;
    protected readonly faComment = faComment;
    protected readonly faGraduationCap = faGraduationCap;
    protected readonly faAlignLeft = faAlignLeft;
    protected readonly getIcon = getIcon;
    protected readonly ExerciseType = ExerciseType;
    protected readonly AssessmentType = AssessmentType;
    protected readonly PlagiarismVerdict = PlagiarismVerdict;

    readonly exercise = input.required<Exercise>();
    readonly studentParticipation = input<StudentParticipation>();
    readonly irisEnabled = input<boolean>(false);
    readonly courseId = input.required<number>();
    readonly gradedStudentParticipation = input<StudentParticipation>();
    readonly plagiarismCaseInfo = input<PlagiarismCaseInfo>();
    readonly latestRatedResult = input<Result>();
    readonly resultWithComplaint = input<Result>();
    readonly allowComplaintsForAutomaticAssessments = input<boolean>(false);
    readonly exampleSolutionInfo = input<ExampleSolutionInfo>();
    readonly participationMode = input<ParticipationMode>('graded');

    /**
     * Stable key describing the sub-route this panel should navigate to. It deliberately captures only the route
     * *identity* (exercise type/id, participation id, mode, online-editor flag) — never the exercise/participation
     * object references. An incoming result replaces the participation object while keeping its id; without this key
     * the navigation effect would re-run on every such change and re-issue router.navigate mid-transition, re-creating
     * the whole code-editor subtree in a loop (flooding the server, see PR #12976). Returns undefined while no
     * navigable target exists yet.
     */
    private readonly navigationTargetKey = computed(() => {
        const exercise = this.exercise();
        if (!exercise?.id) {
            return undefined;
        }
        const participationId = this.studentParticipation()?.id;
        return [exercise.type, exercise.id, participationId ?? '', this.participationMode(), (exercise as ProgrammingExercise).allowOnlineEditor ?? ''].join('|');
    });

    readonly showDiscussion = computed(() => {
        const course = this.exercise().course;
        return !!course && (isCommunicationEnabled(course) || isMessagingEnabled(course));
    });

    private static getChatMode(type: ExerciseType): ChatServiceMode | undefined {
        switch (type) {
            case ExerciseType.PROGRAMMING:
                return ChatServiceMode.PROGRAMMING_EXERCISE;
            case ExerciseType.TEXT:
                return ChatServiceMode.TEXT_EXERCISE;
            default:
                return undefined;
        }
    }

    readonly showIris = computed(() => {
        const exercise = this.exercise();
        return this.irisEnabled() && !!ExerciseSplitPanelComponent.getChatMode(exercise.type!) && !exercise.exerciseGroup;
    });

    readonly irisPanelStartsCollapsed = computed(
        () => this.accountService.userIdentity()?.selectedLLMUsage === LLMSelectionDecision.NO_AI && this.showIris() && !this.showEditorPanel(),
    );

    readonly showCodeEditor = computed(() => {
        const exercise = this.exercise();
        return exercise.type === ExerciseType.PROGRAMMING && (exercise as ProgrammingExercise).allowOnlineEditor;
    });

    readonly showEditorPanel = computed(() => {
        const type = this.exercise().type;
        if (type === ExerciseType.QUIZ) return true;
        if (!this.studentParticipation()) return false;
        if (type === ExerciseType.PROGRAMMING) {
            return (this.exercise() as ProgrammingExercise).allowOnlineEditor ?? false;
        }
        return true;
    });

    readonly editorLabelKey = computed(() => {
        switch (this.exercise().type) {
            case ExerciseType.PROGRAMMING:
                return 'artemisApp.courseOverview.exerciseDetails.codeEditor';
            case ExerciseType.TEXT:
                return 'artemisApp.courseOverview.exerciseDetails.textEditor';
            case ExerciseType.MODELING:
                return 'artemisApp.courseOverview.exerciseDetails.modelingEditor';
            case ExerciseType.FILE_UPLOAD:
                return 'artemisApp.courseOverview.exerciseDetails.fileUploadEditor';
            case ExerciseType.QUIZ:
                return 'artemisApp.courseOverview.exerciseDetails.quizEditor';
            default:
                return 'artemisApp.courseOverview.exerciseDetails.codeEditor';
        }
    });

    readonly usesRouterOutlet = computed(() => {
        const type = this.exercise().type;
        return type === ExerciseType.TEXT || type === ExerciseType.MODELING || type === ExerciseType.FILE_UPLOAD || type === ExerciseType.QUIZ || this.showCodeEditor();
    });

    readonly showComplaintView = computed(() => {
        const exercise = this.exercise();
        const result = this.latestRatedResult();
        return (
            exercise.type === ExerciseType.PROGRAMMING &&
            !!this.gradedStudentParticipation() &&
            !!result &&
            (result.assessmentType === AssessmentType.MANUAL || result.assessmentType === AssessmentType.SEMI_AUTOMATIC || this.allowComplaintsForAutomaticAssessments())
        );
    });

    readonly showRating = computed(() => {
        const result = this.latestRatedResult();
        return (
            this.exercise().type === ExerciseType.PROGRAMMING &&
            !!this.gradedStudentParticipation() &&
            !!result &&
            (result.assessmentType === AssessmentType.MANUAL || result.assessmentType === AssessmentType.SEMI_AUTOMATIC)
        );
    });

    constructor() {
        // Keep _quizBatchStarted / _quizEnded in sync with the exercise input.
        // Effects (unlike computed signals) do not throw NG0950 when reading required inputs,
        // so this is safe even during the initial evaluation before inputs are fully bound.
        effect(() => {
            const exercise = this.exercise();
            const isQuiz = exercise.type === ExerciseType.QUIZ;
            this._quizBatchStarted.set(isQuiz ? ((exercise as QuizExercise).quizBatches?.some((b) => b.started) ?? false) : false);
            this._quizEnded.set(isQuiz ? ((exercise as QuizExercise).quizEnded ?? false) : false);
        });
        effect(() => {
            const exercise = this.exercise();
            const mode = ExerciseSplitPanelComponent.getChatMode(exercise.type!);
            if (this.showIris() && exercise.id && mode) {
                this.chatService.switchTo(mode, exercise.id);
            }
        });
        effect(() => {
            // Depend ONLY on the stable target identity, so object-reference churn (e.g. an incoming result that
            // replaces the participation object but keeps its id) does not re-run this navigation. The imperative
            // navigation runs untracked so it cannot add the exercise/participation objects as dependencies — that
            // combination prevents the navigate-thrash loop that re-created the code-editor subtree (see PR #12976).
            if (!this.navigationTargetKey()) return;
            untracked(() => {
                const participation = this.studentParticipation();
                const exercise = this.exercise();
                const mode = this.participationMode();
                if (!exercise.id) return;

                const type = exercise.type;
                if (type === ExerciseType.QUIZ) {
                    const targetSegment = mode === 'practice' ? 'practice' : 'live';
                    const currentSegment = this.route.firstChild?.snapshot.url[0]?.path;
                    if (currentSegment !== targetSegment) {
                        this.router.navigate(['quiz-exercises', exercise.id, targetSegment], { relativeTo: this.route.parent });
                    }
                    return;
                }
                if (!participation?.id) return;
                const currentParticipationId = this.route.firstChild?.snapshot.paramMap.get('participationId');
                if (currentParticipationId === String(participation.id)) return;
                if (type === ExerciseType.TEXT) {
                    this.router.navigate(['text-exercises', exercise.id, 'participate', participation.id], { relativeTo: this.route.parent });
                } else if (type === ExerciseType.PROGRAMMING && (exercise as ProgrammingExercise).allowOnlineEditor) {
                    this.router.navigate(['programming-exercises', exercise.id, 'code-editor', participation.id], { relativeTo: this.route.parent });
                } else if (type === ExerciseType.MODELING) {
                    this.router.navigate(['modeling-exercises', exercise.id, 'participate', participation.id], { relativeTo: this.route.parent });
                } else if (type === ExerciseType.FILE_UPLOAD) {
                    this.router.navigate(['file-upload-exercises', exercise.id, 'participate', participation.id], { relativeTo: this.route.parent });
                }
            });
        });
    }

    readonly canSubmit = computed(() => {
        const studentParticipation = this.studentParticipation();
        const quizBatchStarted = this._quizBatchStarted();
        const quizHasStarted = this._quizHasStarted();

        // Practice mode on an ended quiz: submittable even before a participation exists.
        // Checked before the guard below, which would short-circuit to false without one.
        if (this.participationMode() === 'practice' && this._quizEnded()) {
            return true;
        }

        // Guard: prevents accessing exercise() before inputs are bound (NG0950).
        // During early init all three are falsy; the effect will update _quizBatchStarted
        // once exercise is set, causing this computed to re-evaluate.
        if (!studentParticipation && !quizBatchStarted && !quizHasStarted) {
            return false;
        }

        const type = this.exercise().type;
        if (type === ExerciseType.QUIZ) {
            // Quiz manages participation internally; check if a batch is started or the student triggered start
            return quizBatchStarted || quizHasStarted;
        }
        if (!studentParticipation) return false;
        if (type === ExerciseType.PROGRAMMING) {
            return (this.exercise() as ProgrammingExercise).allowOnlineEditor ?? false;
        }
        return type === ExerciseType.TEXT || type === ExerciseType.MODELING || type === ExerciseType.FILE_UPLOAD;
    });

    submitExercise(): void {
        const context = this.childrenOutletContexts.getContext('primary');
        if (context?.outlet?.isActivated) {
            const component = context.outlet.component as ExerciseSubmission;
            component.submitExercise();
        }
    }

    restartPractice(): boolean {
        const quizComponent = this._quizComponent();
        if (quizComponent && quizComponent.mode() === 'practice') {
            quizComponent.restartPractice();
            return true;
        }
        return false;
    }

    onOutletActivate(component: any): void {
        if (isQuizComponentRef(component)) {
            this._quizComponent.set(component);
            this.quizStartedSubscription = component.quizStartedEvent.subscribe(() => {
                this._quizHasStarted.set(true);
            });
            this.quizSubmittedSubscription = component.quizSubmittedEvent.subscribe((submission: QuizSubmission) => {
                this.quizSubmitted.emit(submission);
            });
            this.liveQuizStatusSubscription = component.liveQuizStatusChange.subscribe((status: LiveQuizParticipationStatus | undefined) => {
                this.liveQuizStatusChange.emit(status);
            });
            this.quizPracticeParticipationSubscription = component.practiceParticipationChanged.subscribe((participation: StudentParticipation) => {
                this.quizPracticeParticipationChanged.emit(participation);
            });
            this.liveQuizResultSubscription = component.liveQuizResultParticipation.subscribe((participation: StudentParticipation) => {
                this.liveQuizResultParticipation.emit(participation);
            });
        }
    }

    onOutletDeactivate(): void {
        this._quizComponent.set(undefined);
        this.quizStartedSubscription?.unsubscribe();
        this.quizStartedSubscription = undefined;
        this.quizSubmittedSubscription?.unsubscribe();
        this.quizSubmittedSubscription = undefined;
        this.liveQuizStatusSubscription?.unsubscribe();
        this.liveQuizStatusSubscription = undefined;
        this.quizPracticeParticipationSubscription?.unsubscribe();
        this.quizPracticeParticipationSubscription = undefined;
        this.liveQuizResultSubscription?.unsubscribe();
        this.liveQuizResultSubscription = undefined;
        this._quizHasStarted.set(false);
    }
}
