import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { ActivatedRoute, ChildrenOutletContexts, Router, RouterLink, RouterOutlet } from '@angular/router';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { faAlignLeft, faComment, faGear, faGraduationCap } from '@fortawesome/free-solid-svg-icons';
import { ProblemStatementComponent } from 'app/core/course/overview/exercise-details/problem-statement/problem-statement.component';
import { TextEditorComponent } from 'app/text/overview/text-editor/text-editor.component';
import { CodeEditorStudentContainerComponent } from 'app/programming/overview/code-editor-student-container/code-editor-student-container.component';
import { ModelingSubmissionComponent } from 'app/modeling/overview/modeling-submission/modeling-submission.component';
import { FileUploadSubmissionComponent } from 'app/fileupload/overview/file-upload-submission/file-upload-submission.component';
import { QuizParticipationComponent } from 'app/quiz/overview/participation/quiz-participation.component';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ParticipationMode } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { isCommunicationEnabled, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared/components/resizable-panels/resizable-panels.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ResetRepoButtonComponent } from 'app/core/course/overview/exercise-details/reset-repo-button/reset-repo-button.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { RatingComponent } from 'app/exercise/rating/rating.component';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ProgrammingExerciseExampleSolutionRepoDownloadComponent } from 'app/programming/shared/actions/example-solution-repo-download/programming-exercise-example-solution-repo-download.component';
import { CompetencyContributionComponent } from 'app/atlas/shared/competency-contribution/competency-contribution.component';
import { LtiInitializerComponent } from 'app/core/course/overview/exercise-details/lti-initializer/lti-initializer.component';
import { PanelModule } from 'primeng/panel';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ExampleSolutionInfo } from 'app/exercise/services/exercise.service';
import { DiscussionSectionComponent } from 'app/communication/shared/discussion-section/discussion-section.component';

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
    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    private readonly childrenOutletContexts = inject(ChildrenOutletContexts);
    // Tracks whether a quiz batch is started from the server-provided exercise data.
    // Updated via effect (safe for required inputs) rather than computed (would throw NG0950 during early init).
    private readonly _quizBatchStarted = signal(false);
    private readonly _quizHasStarted = signal(false);
    private readonly _quizComponent = signal<QuizParticipationComponent | undefined>(undefined);
    private quizStartedSubscription: { unsubscribe(): void } | undefined;

    readonly quizSubmitDisabled = computed(() => this._quizComponent()?.isSubmitDisabled() ?? false);
    readonly quizSubmitTitle = computed(() => this._quizComponent()?.submitTitleKey() ?? 'entity.action.submit');
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
        // Keep _quizBatchStarted in sync with the exercise input.
        // Effects (unlike computed signals) do not throw NG0950 when reading required inputs,
        // so this is safe even during the initial evaluation before inputs are fully bound.
        effect(() => {
            const exercise = this.exercise();
            const started = exercise.type === ExerciseType.QUIZ ? ((exercise as QuizExercise).quizBatches?.some((b) => b.started) ?? false) : false;
            this._quizBatchStarted.set(started);
        });
        effect(() => {
            const exercise = this.exercise();
            const mode = ExerciseSplitPanelComponent.getChatMode(exercise.type!);
            if (this.showIris() && exercise.id && mode) {
                this.chatService.switchTo(mode, exercise.id);
            }
        });
        effect(() => {
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
    }

    readonly canSubmit = computed(() => {
        const studentParticipation = this.studentParticipation();
        const quizBatchStarted = this._quizBatchStarted();
        const quizHasStarted = this._quizHasStarted();

        // Guard: prevents accessing exercise() before inputs are bound (NG0950).
        // During early init all three are falsy; the effect will update _quizBatchStarted
        // once exercise is set, causing this computed to re-evaluate.
        if (!studentParticipation && !quizBatchStarted && !quizHasStarted) {
            return false;
        }

        const type = this.exercise().type;
        if (type === ExerciseType.QUIZ) {
            // Quiz manages participation internally; check if a batch is started or the student triggered start
            return quizBatchStarted || quizHasStarted || (this.participationMode() === 'practice' && ((this.exercise() as QuizExercise).quizEnded ?? false));
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
            const component = context.outlet.component;
            if (component instanceof TextEditorComponent) {
                component.submit();
            } else if (component instanceof CodeEditorStudentContainerComponent) {
                component.commit();
            } else if (component instanceof ModelingSubmissionComponent) {
                component.submit();
            } else if (component instanceof FileUploadSubmissionComponent) {
                component.submitExercise();
            } else if (component instanceof QuizParticipationComponent) {
                component.onSubmit();
            }
        }
    }

    restartPractice(): boolean {
        const quizComponent = this._quizComponent();
        if (quizComponent && quizComponent.mode === 'practice') {
            quizComponent.restartPractice();
            return true;
        }
        return false;
    }

    onOutletActivate(component: any): void {
        if (component instanceof QuizParticipationComponent) {
            this._quizComponent.set(component);
            this.quizStartedSubscription = component.quizStartedEvent.subscribe(() => {
                this._quizHasStarted.set(true);
            });
        }
    }

    onOutletDeactivate(): void {
        this._quizComponent.set(undefined);
        this.quizStartedSubscription?.unsubscribe();
        this.quizStartedSubscription = undefined;
        this._quizHasStarted.set(false);
    }
}
