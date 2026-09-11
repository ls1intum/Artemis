import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { ChangeDetectionStrategy, Component, DestroyRef, Injector, OnDestroy, computed, inject, linkedSignal, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import {
    faArrowLeft,
    faArrowRight,
    faBan,
    faCheckDouble,
    faCircleExclamation,
    faCircleInfo,
    faCircleNotch,
    faPaperPlane,
    faPlus,
    faSave,
    faSpinner,
    faTableColumns,
    faTimes,
    faTimesCircle,
    faTriangleExclamation,
} from '@fortawesome/free-solid-svg-icons';
import { MarkdownEditorHeight } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from '../../status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { AlertService } from 'app/foundation/service/alert.service';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Observable, Subject, finalize, take, takeUntil, tap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProblemStatementAiOperationsHelper } from 'app/programming/manage/shared/problem-statement-ai-operations.helper';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import {
    TumUiButtonDirective,
    TumUiConfirmDialogComponent,
    TumUiConfirmationService,
    TumUiDialogComponent,
    TumUiInputDirective,
    TumUiPopoverComponent,
    TumUiStatusDotComponent,
} from '@tumaet/ui-angular';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/editor/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssueCategoryEnum, ConsistencyIssueSeverityEnum } from 'app/openapi/model/consistency-issue';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { ReviewAdaptExerciseDialogComponent, ReviewAdaptExerciseDialogResult } from 'app/exercise/review/adapt-exercise-dialog/review-adapt-exercise-dialog.component';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContent, CommentContentType, ConsistencyIssueCommentContent } from 'app/exercise/shared/entities/review/comment-content.model';
import { CommentThread, CommentThreadLocationType, ReviewThreadLocation } from 'app/exercise/shared/entities/review/comment-thread.model';
import { AdaptFinding, getFirstCommentByCreatedDateThenId, selectedThreadsFindings } from 'app/exercise/review/review-comment-utils';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { ProblemStatementService } from 'app/programming/manage/services/problem-statement.service';
import { InlineRefinementEvent, MAX_USER_PROMPT_LENGTH } from 'app/programming/manage/shared/problem-statement.utils';
import { TooltipModule } from 'primeng/tooltip';
import { BadgeModule } from 'primeng/badge';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { Popover, PopoverModule } from 'primeng/popover';
import { HyperionGenerationActivityFacade, HyperionGenerationCompletedEvent } from 'app/hyperion/exercise-generation/hyperion-generation-activity.facade';
import { Router, RouterLink } from '@angular/router';
import { isHyperionGenerationDraft, supportsHyperionExerciseGeneration } from 'app/hyperion/exercise-generation/hyperion-generation-support';

const SEVERITY_ORDER: Record<ConsistencyIssueSeverityEnum, number> = {
    ['HIGH']: 0,
    ['MEDIUM']: 1,
    ['LOW']: 2,
};

const APPLIED_GENERATION_REFRESH_STATE = 'appliedHyperionGenerationRefresh';
const HYPERION_RELOAD_CONFIRMATION_KEY = 'hyperionReloadSavedExerciseConfirmation';

interface AppliedGenerationRefresh {
    exerciseId: number;
    jobId: string;
}

function isAppliedGenerationRefresh(value: unknown): value is AppliedGenerationRefresh {
    const candidate = value as AppliedGenerationRefresh | undefined;
    return typeof candidate === 'object' && candidate !== null && typeof candidate.exerciseId === 'number' && typeof candidate.jobId === 'string';
}

interface ConsistencyIssueNavigationIssue {
    threadId: number;
    targetType: CommentThreadLocationType;
    filePath?: string;
    lineNumber?: number;
    auxiliaryRepositoryId?: number;
    severity: ConsistencyIssueSeverityEnum;
    category: ConsistencyIssueCategoryEnum;
}

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-and-editor-container.component.html',
    styleUrl: 'code-editor-instructor-and-editor-container.scss',
    // Keep review comment state scoped to each editor container instance.
    providers: [ExerciseReviewCommentService, TumUiConfirmationService, HyperionGenerationActivityFacade],
    imports: [
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        CodeEditorContainerComponent,
        IncludedInScoreBadgeComponent,
        ProgrammingExerciseInstructorExerciseStatusComponent,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        NgbDropdownItem,
        NgbTooltip,
        UpdatingResultComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseEditableInstructionComponent,
        ProgrammingExerciseInstructionComponent,
        FormsModule,
        A11yModule,
        GitDiffLineStatComponent,
        TooltipModule,
        TumUiInputDirective,
        TumUiPopoverComponent,
        BadgeModule,
        ButtonModule,
        MessageModule,
        PopoverModule,
        TumUiButtonDirective,
        TumUiStatusDotComponent,
        RouterLink,
        TumUiConfirmDialogComponent,
        TumUiDialogComponent,
        ReviewAdaptExerciseDialogComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CodeEditorInstructorAndEditorContainerComponent extends CodeEditorInstructorBaseContainerComponent implements OnDestroy {
    readonly resultComp = viewChild(UpdatingResultComponent);
    readonly editableInstructions = viewChild(ProgrammingExerciseEditableInstructionComponent);
    protected readonly generationActivity = inject(HyperionGenerationActivityFacade);

    readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly MAX_USER_PROMPT_LENGTH = MAX_USER_PROMPT_LENGTH;
    readonly MarkdownEditorHeight = MarkdownEditorHeight;
    readonly sortedIssues = computed(() =>
        this.exerciseReviewCommentService
            .threads()
            .filter((thread) => thread.resolved !== true)
            .map((thread) => this.mapConsistencyThreadToNavigationIssue(thread))
            .filter((issue): issue is ConsistencyIssueNavigationIssue => issue !== undefined)
            .sort((a, b) => (SEVERITY_ORDER[a.severity] ?? SEVERITY_ORDER['MEDIUM']) - (SEVERITY_ORDER[b.severity] ?? SEVERITY_ORDER['MEDIUM']) || a.threadId - b.threadId),
    );

    /** Shared helper that encapsulates all AI-powered problem statement operations. */
    readonly aiOps = new ProblemStatementAiOperationsHelper(
        inject(ProblemStatementService),
        inject(AlertService),
        inject(ArtemisIntelligenceService),
        inject(ProfileService),
        inject(DestroyRef),
        inject(Injector),
    );

    // Delegate signals for template binding compatibility
    protected readonly allowSplitView = this.aiOps.allowSplitView;
    protected readonly addedLineCount = this.aiOps.addedLineCount;
    protected readonly removedLineCount = this.aiOps.removedLineCount;
    protected readonly isGeneratingOrRefining = this.aiOps.isGeneratingOrRefining;
    protected readonly isAiApplying = this.aiOps.isAiApplying;
    readonly showDiff = this.aiOps.showDiff;
    readonly hyperionEnabled = this.aiOps.hyperionEnabled;
    readonly hyperionGenerationSupported = this.aiOps.hyperionGenerationSupported;
    protected readonly isPromptNearLimit = this.aiOps.isPromptNearLimit;
    readonly shouldShowGenerateButton = this.aiOps.shouldShowGenerateButton;

    readonly faTableColumns = faTableColumns;
    override readonly ButtonSize = ButtonSize;

    readonly refinementPopover = viewChild<TumUiPopoverComponent>('refinementPopover');
    /** Prompt bound to the refinement popover textarea — aliased to aiOps.userPrompt. */
    readonly refinementPrompt = this.aiOps.userPrompt;
    protected readonly faPaperPlane = faPaperPlane;

    private consistencyCheckService = inject(ConsistencyCheckService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private exerciseReviewCommentService = inject(ExerciseReviewCommentService);

    lineJumpOnFileLoad: number | undefined = undefined;
    fileToJumpOn: string | undefined = undefined;
    private repositorySwitchTarget: { repository: RepositoryType; auxiliaryRepositoryId?: number } | undefined;
    /**
     * The issue the consistency toolbar is parked on. It is derived from {@link sortedIssues} — while the toolbar is
     * open the selection falls back to the first issue whenever the current one disappears — but the toolbar's
     * next/previous buttons also set it directly, which is exactly what `linkedSignal` is for.
     */
    readonly selectedIssue = linkedSignal<{ toolbarVisible: boolean; issues: ConsistencyIssueNavigationIssue[] }, ConsistencyIssueNavigationIssue | undefined>({
        source: () => ({ toolbarVisible: this.showConsistencyIssuesToolbar(), issues: this.sortedIssues() }),
        computation: ({ toolbarVisible, issues }, previous) => {
            const current = previous?.value;
            if (!toolbarVisible || (current && issues.some((issue) => issue.threadId === current.threadId))) {
                return current;
            }
            return issues[0] ?? current;
        },
    });
    readonly generationStartPending = signal(false);
    readonly generationRefreshPending = signal(false);
    readonly generationRefreshFailed = signal(false);
    readonly generationRefreshBaselineUnknown = signal(false);
    readonly problemStatementHasUnsavedChanges = signal(false);
    readonly adaptSubmissionError = signal<string | undefined>(undefined);
    readonly adaptDialogVisible = signal(false);
    readonly adaptDialogFindings = signal<AdaptFinding[]>([]);
    readonly adaptDialogSelectedIds = signal<number[]>([]);
    private generationStartSequence = 0;
    private pendingGenerationRefreshJobId?: string;
    /** Present exactly while an adapt dialog opened by {@link openAdaptDialog} is still awaiting the user's decision. */
    private pendingAdaptDialog?: { exerciseId: number; onCancel?: () => void };
    private readonly generationRegistry = inject(HyperionJobRegistryService);
    private readonly exerciseChanged = new Subject<void>();

    // Icons
    protected readonly faPlus = faPlus;
    protected readonly faTimes = faTimes;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faTimesCircle = faTimesCircle;
    protected readonly faSave = faSave;
    protected readonly faBan = faBan;
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faArrowRight = faArrowRight;
    protected readonly faCircleExclamation = faCircleExclamation;
    protected readonly faTriangleExclamation = faTriangleExclamation;
    protected readonly faCircleInfo = faCircleInfo;

    protected readonly faSpinner = faSpinner;
    protected readonly facArtemisIntelligence = facArtemisIntelligence;

    protected readonly RepositoryType = RepositoryType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faCheckDouble = faCheckDouble;
    private confirmationService = inject(TumUiConfirmationService);
    private generationService = inject(HyperionExerciseGenerationService);
    private reviewRouter = inject(Router);
    private readonly editorDestroyRef = inject(DestroyRef);
    private appliedGenerationRefresh: AppliedGenerationRefresh | undefined = (() => {
        const value: unknown = this.reviewRouter.currentNavigation()?.extras.state?.[APPLIED_GENERATION_REFRESH_STATE];
        return isAppliedGenerationRefresh(value) ? value : undefined;
    })();
    private readonly selectedAdaptFeedbackThreads = computed(() =>
        this.exerciseReviewCommentService.selectedFeedbackThreads().filter((thread) => !thread.resolved && !thread.outdated),
    );
    private readonly selectedAdaptFeedbackThreadIds = computed(() =>
        this.selectedAdaptFeedbackThreads()
            .map((thread) => thread.id)
            .filter((threadId): threadId is number => threadId !== undefined),
    );
    readonly selectedAdaptFeedbackCount = computed(() => this.selectedAdaptFeedbackThreadIds().length);

    constructor() {
        super();
        this.aiOps.setChangeHandler({
            onContentChanged: (content, exercise) => {
                const currentExerciseId = this.exercise()?.id;
                if (currentExerciseId && exercise?.id && currentExerciseId !== exercise.id) {
                    return; // Ignore stale async results from a different exercise
                }
                this.onInstructionChanged(content);
            },
        });
        this.generationActivity.connect({
            exerciseId: computed(() => (this.generationSupported() ? this.exercise()?.id : undefined)),
            refreshingEditor: this.generationRefreshPending,
        });
        this.generationActivity.generationCompleted.pipe(takeUntilDestroyed()).subscribe((event) => this.onHyperionGenerationCompleted(event));
        this.generationActivity.generationReverted.pipe(takeUntilDestroyed()).subscribe(() => this.refreshAfterHyperionRepositoryChange());
    }

    override loadExercise(exerciseId: number): Observable<ProgrammingExercise> {
        const currentExerciseId = this.exercise()?.id;
        if (currentExerciseId !== undefined && currentExerciseId !== exerciseId) {
            this.invalidateHyperionLifecycleState();
        }
        return super.loadExercise(exerciseId).pipe(
            tap((exercise) => {
                this.problemStatementHasUnsavedChanges.set(false);
                if (exercise.id) {
                    this.connectExerciseEditorSync(exercise.id);
                    this.exerciseReviewCommentService.setExercise(exercise.id);
                    this.exerciseReviewCommentService.reloadThreads();
                }
                this.aiOps.loadTemplate(exercise);
                this.aiOps.currentProblemStatement.set(exercise.problemStatement ?? '');
            }),
        );
    }

    /**
     * Clears draft widgets and reloads review comment threads after a commit.
     */
    onCommit(): void {
        this.codeEditorContainer()?.monacoEditor()?.clearReviewCommentDrafts();
        this.exerciseReviewCommentService.reloadThreads();
    }

    /**
     * Clears problem-statement draft widgets and reloads review comment threads after saving.
     */
    onProblemStatementSaved(): void {
        this.problemStatementHasUnsavedChanges.set(false);
        this.editableInstructions()?.clearReviewCommentDrafts();
        this.exerciseReviewCommentService.reloadThreads();
    }

    protected onProblemStatementUnsavedChangesChanged(hasUnsavedChanges: boolean): void {
        this.problemStatementHasUnsavedChanges.set(hasUnsavedChanges);
    }

    protected onHyperionGenerationCompleted(event: HyperionGenerationCompletedEvent): void {
        if (event.completionStatus === 'NEEDS_REVIEW') {
            this.exerciseReviewCommentService.reloadThreads();
        }
        if (event.liveExerciseChanged === true) {
            if (this.wasGenerationRefreshApplied(event.jobId)) {
                return;
            }
            this.pendingGenerationRefreshJobId = event.jobId;
            this.refreshAfterHyperionRepositoryChange();
        }
    }

    protected readonly generationLink = computed(() => {
        const exerciseId = this.exercise()?.id;
        const courseId = this.exercise()?.course?.id;
        return exerciseId !== undefined && courseId !== undefined ? ['/course-management', courseId, 'programming-exercises', exerciseId, 'generation'] : undefined;
    });

    protected openGenerationPage(): void {
        const link = this.generationLink();
        if (link) {
            void this.reviewRouter.navigate(link);
        }
    }

    protected onAiToolbarClick(event: Event, popover: Popover): void {
        if (this.isExerciseGenerationActionBlocked()) {
            this.openGenerationPage();
            return;
        }
        popover.toggle(event);
    }

    private refreshAfterHyperionRepositoryChange(): void {
        const exerciseId = this.exercise()?.id;
        if (exerciseId === undefined || this.generationRefreshPending()) {
            return;
        }
        if (this.adaptDialogVisible() || !this.canRefreshAfterHyperionRepositoryChange()) {
            this.generationRefreshFailed.set(true);
            this.generationRefreshBaselineUnknown.set(true);
            this.alertService.warning('artemisApp.hyperion.generationActivity.refreshBlockedByLocalEdits');
            return;
        }
        this.generationRefreshFailed.set(false);
        this.generationRefreshBaselineUnknown.set(false);
        this.markGenerationRefreshApplied(exerciseId, this.pendingGenerationRefreshJobId);
        this.generationRefreshPending.set(true);
        this.reloadEditor();
    }

    protected retryHyperionRefresh(): void {
        const exerciseId = this.exercise()?.id;
        const jobId = this.pendingGenerationRefreshJobId;
        if (exerciseId === undefined || jobId === undefined || !this.generationRefreshFailed() || this.generationRefreshPending()) {
            return;
        }
        this.confirmationService.confirm({
            key: HYPERION_RELOAD_CONFIRMATION_KEY,
            header: this.translateService.instant('artemisApp.hyperion.generationActivity.reloadSavedExerciseConfirmHeader'),
            message: this.translateService.instant('artemisApp.hyperion.generationActivity.reloadSavedExerciseConfirmMessage'),
            rejectLabel: this.translateService.instant('entity.action.cancel'),
            acceptLabel: this.translateService.instant('artemisApp.hyperion.generationActivity.reloadSavedExercise'),
            acceptSeverity: 'danger',
            accept: () => this.reloadSavedExercise(exerciseId, jobId),
        });
    }

    private reloadEditor(): void {
        window.location.reload();
    }

    private reloadSavedExercise(exerciseId: number, jobId: string): void {
        if (this.generationRefreshPending() || this.exercise()?.id !== exerciseId || this.pendingGenerationRefreshJobId !== jobId) {
            return;
        }
        this.generationRefreshFailed.set(false);
        this.generationRefreshBaselineUnknown.set(false);
        this.markGenerationRefreshApplied(exerciseId, jobId);
        this.generationRefreshPending.set(true);
        this.codeEditorContainer()?.allowNextUnloadWithoutConfirmation();
        this.reloadEditor();
    }

    private wasGenerationRefreshApplied(jobId: string): boolean {
        const applied = this.appliedGenerationRefresh;
        return applied?.exerciseId === this.exercise()?.id && applied?.jobId === jobId;
    }

    private markGenerationRefreshApplied(exerciseId: number, jobId: string | undefined): void {
        if (jobId === undefined) {
            return;
        }
        this.appliedGenerationRefresh = { exerciseId, jobId };
        this.persistNavigationStateEntry(APPLIED_GENERATION_REFRESH_STATE, this.appliedGenerationRefresh);
        if (this.pendingGenerationRefreshJobId === jobId) {
            this.pendingGenerationRefreshJobId = undefined;
        }
    }

    /**
     * Patches a single key into the state of the *current* history entry.
     *
     * Both markers written here (the applied-refresh job and the consumed auto-start flag) exist for exactly one
     * reason: to survive the full document reload performed by {@link reloadEditor}. They are read back through the
     * Router — Angular copies the restored entry's state into `Navigation.extras.state` on the initial navigation —
     * but they cannot be *written* through it. The Router's only public write path is a navigation, and a
     * same-URL navigation runs the whole transition (guards included) and may be cancelled, which would silently
     * drop the marker and leave the reload unguarded against repeating itself.
     */
    private persistNavigationStateEntry(key: string, value: unknown): void {
        const historyState: Record<string, unknown> = deepClone(window.history.state ?? {});
        historyState[key] = value;
        window.history.replaceState(historyState, '');
    }

    private canRefreshAfterHyperionRepositoryChange(): boolean {
        const codeEditor = this.codeEditorContainer();
        const codeEditorClean =
            (codeEditor?.canDeactivate?.() ?? false) && (codeEditor?.hasCleanRepositoryState?.() ?? false) && !(codeEditor?.hasReviewCommentDrafts?.() ?? false);
        const editableInstructions = this.editableInstructions();
        const problemStatementClean =
            !this.problemStatementHasUnsavedChanges() && !(editableInstructions?.unsavedChangesValue?.() ?? false) && !(editableInstructions?.hasReviewCommentDrafts?.() ?? false);
        return codeEditorClean && problemStatementClean;
    }

    protected readonly canGenerateExercise = computed(() => isHyperionGenerationDraft(this.exercise(), Date.now()));

    protected readonly generationSupported = computed(() => {
        const exercise = this.exercise();
        return (
            this.hyperionGenerationSupported &&
            !!exercise?.id &&
            (exercise?.isAtLeastEditor ?? false) &&
            supportsHyperionExerciseGeneration(exercise?.programmingLanguage, exercise?.projectType)
        );
    });

    protected readonly isExerciseGenerationRunning = computed(() => {
        const activity = this.generationActivity;
        return this.generationStartPending() || this.generationRefreshPending() || (this.generationSupported() && (activity.statusLoading() || activity.running() || false));
    });

    protected readonly isExerciseGenerationActionBlocked = computed(() => {
        const activity = this.generationActivity;
        return this.isExerciseGenerationRunning() || this.generationRefreshFailed() || (this.generationSupported() && activity.statusLoadFailed());
    });

    protected readonly isProblemStatementEditingLocked = computed(() => {
        const activity = this.generationActivity;
        return this.isExerciseGenerationRunning() || this.generationRefreshBaselineUnknown() || (this.generationSupported() && activity.statusLoadFailed());
    });

    protected readonly adaptBlockedReason = computed(() => {
        if (!this.canGenerateExercise()) {
            return 'artemisApp.review.adaptExercise.ineligible';
        }
        if (this.generationStartPending()) {
            return 'artemisApp.review.adaptExercise.starting';
        }
        if (this.generationActivity.running()) {
            return 'artemisApp.review.adaptExercise.runInProgress';
        }
        if (this.generationActivity.statusLoading()) {
            return 'artemisApp.review.adaptExercise.checkingStatus';
        }
        if (this.generationActivity.statusLoadFailed()) {
            return 'artemisApp.review.adaptExercise.statusUnavailable';
        }
        if (this.generationRefreshPending() || this.generationRefreshFailed()) {
            return this.adaptDialogVisible() ? 'artemisApp.review.adaptExercise.reloadDraftRequired' : 'artemisApp.review.adaptExercise.reloadRequired';
        }
        return undefined;
    });

    protected readonly canAdaptWithFeedback = computed(() => this.generationSupported() && this.canGenerateExercise());

    protected adaptFromThread(threadId: number): void {
        if (!this.canAdaptWithFeedback() || this.isExerciseGenerationActionBlocked()) {
            return;
        }
        const wasAlreadySelected = this.exerciseReviewCommentService.selectedFeedbackThreadIds().includes(threadId);
        this.exerciseReviewCommentService.selectThreadAsFeedback(threadId);
        this.openAdaptDialog(wasAlreadySelected ? undefined : () => this.exerciseReviewCommentService.toggleThreadFeedbackSelection(threadId));
    }

    protected openAdaptDialog(onCancel?: () => void): void {
        if (!this.canAdaptWithFeedback() || this.isExerciseGenerationActionBlocked()) {
            return;
        }
        const exerciseId = this.exercise()?.id;
        if (exerciseId === undefined) {
            return;
        }
        if (!this.canRefreshAfterHyperionRepositoryChange()) {
            onCancel?.();
            this.alertService.warning('artemisApp.hyperion.generationActivity.saveChangesFirst');
            return;
        }
        this.adaptSubmissionError.set(undefined);
        this.adaptDialogFindings.set(
            selectedThreadsFindings(
                this.exerciseReviewCommentService
                    .threads()
                    .filter((thread) => !thread.resolved && !thread.outdated && thread.targetType !== CommentThreadLocationType.AUXILIARY_REPO),
                this.translateService,
            ),
        );
        this.adaptDialogSelectedIds.set(this.selectedAdaptFeedbackThreadIds());
        this.pendingAdaptDialog = { exerciseId, onCancel };
        this.adaptDialogVisible.set(true);
    }

    protected onAdaptDialogConfirmed(result: ReviewAdaptExerciseDialogResult): void {
        const pending = this.pendingAdaptDialog;
        if (!pending || this.exercise()?.id !== pending.exerciseId || this.isExerciseGenerationActionBlocked()) {
            return;
        }
        if (result.selectedFeedbackThreadIds !== undefined) {
            this.exerciseReviewCommentService.selectedFeedbackThreadIds.set(result.selectedFeedbackThreadIds);
        }
        this.startAdaptation(result.instructions);
    }

    /** Runs for every dismissal — the cancel button, Escape, the backdrop, and the close icon alike. */
    protected onAdaptDialogHidden(): void {
        const pending = this.pendingAdaptDialog;
        this.pendingAdaptDialog = undefined;
        if (pending && this.exercise()?.id === pending.exerciseId) {
            pending.onCancel?.();
        }
    }

    private startAdaptation(instructions?: string): void {
        const exerciseId = this.exercise()?.id;
        if (exerciseId === undefined || this.isExerciseGenerationActionBlocked()) {
            return;
        }
        if (!this.canRefreshAfterHyperionRepositoryChange()) {
            this.alertService.warning('artemisApp.hyperion.generationActivity.saveChangesFirst');
            return;
        }
        const selectedFeedbackThreadIds = this.selectedAdaptFeedbackThreadIds();
        const requestSequence = ++this.generationStartSequence;
        this.adaptSubmissionError.set(undefined);
        this.generationStartPending.set(true);
        this.generationService
            .generate(exerciseId, {
                mode: 'ADAPT',
                prompt: instructions,
                selectedFeedbackThreadIds: selectedFeedbackThreadIds.length > 0 ? selectedFeedbackThreadIds : undefined,
            })
            .pipe(
                take(1),
                takeUntil(this.exerciseChanged),
                takeUntilDestroyed(this.editorDestroyRef),
                finalize(() => {
                    if (requestSequence === this.generationStartSequence) {
                        this.generationStartPending.set(false);
                    }
                }),
            )
            .subscribe({
                next: ({ jobId }) => {
                    if (requestSequence !== this.generationStartSequence || this.exercise()?.id !== exerciseId) {
                        return;
                    }
                    const exercise = this.exercise();
                    if (exercise?.course?.id !== undefined) {
                        this.generationRegistry.track({ jobId, exerciseId, courseId: exercise.course.id, exerciseTitle: exercise.title ?? '', mode: 'ADAPT' });
                    }
                    this.pendingAdaptDialog = undefined;
                    this.adaptDialogVisible.set(false);
                    this.exerciseReviewCommentService.clearSelectedFeedback();
                    this.generationActivity.attachToJob(jobId, 'ADAPT');
                    this.openGenerationPage();
                },
                error: (error: unknown) => {
                    if (requestSequence === this.generationStartSequence && this.exercise()?.id === exerciseId) {
                        const body = error instanceof HttpErrorResponse ? (error.error as { errorKey?: string } | undefined) : undefined;
                        const knownError = body?.errorKey === 'generationCapacityUnavailable' || body?.errorKey === 'exerciseGenerationRunning';
                        this.adaptSubmissionError.set(knownError ? 'error.' + body.errorKey : 'artemisApp.review.adaptExercise.startFailed');
                        if (!this.adaptDialogVisible()) {
                            this.alertService.error('artemisApp.hyperion.generationActivity.adaptStartFailed');
                        }
                    }
                },
            });
    }

    override selectTemplateParticipation(): Promise<boolean> {
        return super.selectTemplateParticipation();
    }

    override selectSolutionParticipation(): Promise<boolean> {
        return super.selectSolutionParticipation();
    }

    override selectAssignmentParticipation(): Promise<boolean> {
        return super.selectAssignmentParticipation();
    }

    override selectTestRepository(): Promise<boolean> {
        return super.selectTestRepository();
    }

    override selectAuxiliaryRepository(repositoryId: number): Promise<boolean> {
        return super.selectAuxiliaryRepository(repositoryId);
    }

    override ngOnDestroy() {
        this.closeHyperionOverlays();
        this.exerciseChanged.next();
        this.exerciseChanged.complete();
        this.aiOps.destroy();
        super.ngOnDestroy();
    }

    private invalidateHyperionLifecycleState(): void {
        this.generationStartSequence++;
        this.closeHyperionOverlays();
        this.exerciseChanged.next();
        this.generationStartPending.set(false);
        this.generationRefreshPending.set(false);
        this.generationRefreshFailed.set(false);
        this.generationRefreshBaselineUnknown.set(false);
        this.problemStatementHasUnsavedChanges.set(false);
        this.pendingGenerationRefreshJobId = undefined;
    }

    private closeHyperionOverlays(): void {
        this.confirmationService.close(HYPERION_RELOAD_CONFIRMATION_KEY);
        // Dropping the pending decision before hiding keeps this programmatic close from running the cancel callback.
        this.pendingAdaptDialog = undefined;
        this.adaptDialogVisible.set(false);
        this.refinementPopover()?.close();
    }

    /**
     * Checks whether a consistency check operation is currently running.
     *
     * @returns {boolean} `true` if either the rewrite or consistency check process is currently loading; otherwise `false`.
     */
    isCheckingConsistency(): boolean {
        return this.artemisIntelligenceService.isLoading();
    }

    /**
     * Runs a consistency check for the given programming exercise.
     *
     * First verifies that all required repositories are set up correctly.
     * If no setup issues are found, performs a full content consistency check.
     * Displays alerts for errors, warnings, or successful results.
     *
     * @param {ProgrammingExercise} exercise - The exercise to check.
     */
    checkConsistencies(exercise: ProgrammingExercise) {
        if (this.isExerciseGenerationActionBlocked()) {
            this.openGenerationPage();
            return;
        }
        this.selectedIssue.set(undefined);
        this.showConsistencyIssuesToolbar.set(false);
        const existingConsistencyThreadIds = new Set(
            this.exerciseReviewCommentService
                .threads()
                .filter((thread) => this.extractConsistencyIssueContent(thread) !== undefined)
                .map((thread) => thread.id)
                .filter((id): id is number => id !== undefined),
        );

        if (!exercise.id) {
            this.alertService.error(this.translateService.instant('artemisApp.hyperion.consistencyCheck.checkFailedAlert'));
            return;
        }

        this.consistencyCheckService.checkConsistencyForProgrammingExercise(exercise.id).subscribe({
            // This first consistency check ensures, that the exercise has all repositories set up
            // This does not yet check the actual content of the exercise
            next: (inconsistencies: ConsistencyCheckError[]) => {
                if (inconsistencies.length > 0) {
                    for (const inconsistency of inconsistencies) {
                        this.alertService.error(this.translateService.instant(`artemisApp.consistencyCheck.error.${inconsistency.type}`));
                    }
                    return;
                }

                // Now the content is checked
                this.artemisIntelligenceService.consistencyCheck(exercise.id!).subscribe({
                    next: () => {
                        this.exerciseReviewCommentService.reloadThreads(() => {
                            const hasNewPersistedIssues = this.sortedIssues().some((issue) => !existingConsistencyThreadIds.has(issue.threadId));
                            if (!hasNewPersistedIssues) {
                                this.alertService.success(this.translateService.instant('artemisApp.hyperion.consistencyCheck.noInconsistencies'));
                                return;
                            }
                            this.alertService.warning(this.translateService.instant('artemisApp.hyperion.consistencyCheck.inconsistenciesFoundAlert'));
                            this.showConsistencyIssuesToolbar.set(true);
                            // Opening the toolbar re-derives the selection, so the jump belongs to this action rather
                            // than to a reactive effect watching the selection.
                            this.jumpToSelectedIssue();
                        });
                    },
                    error: () => {
                        this.alertService.error(this.translateService.instant('artemisApp.hyperion.consistencyCheck.checkFailedAlert'));
                    },
                });
            },
            error: () => {
                this.alertService.error(this.translateService.instant('artemisApp.hyperion.consistencyCheck.checkFailedAlert'));
            },
        });
    }

    /**
     * Returns the appropriate FontAwesome icon for the given severity.
     *
     * @param {ConsistencyIssueSeverityEnum} severity
     *        The severity that determines the returned icon.
     *
     * @returns
     *          A FontAwesome icon representing high, medium, or low severity.
     */
    getSeverityIcon(severity: ConsistencyIssueSeverityEnum | undefined) {
        switch (severity) {
            case 'HIGH':
                return this.faCircleExclamation;
            case 'MEDIUM':
                return this.faTriangleExclamation;
            case 'LOW':
                return this.faCircleInfo;
            default:
                return this.faCircleInfo;
        }
    }

    /**
     * Reverts all changes made during the refinement session and restores the original/snapshot state.
     * Syncs the reverted content back to the model.
     */
    revertAllRefinement(): void {
        this.aiOps.revertAllChanges(this.exercise(), this.editableInstructions());
    }

    /**
     * Closes the diff view after syncing the current editor content to the model.
     */
    closeDiff(): void {
        this.aiOps.closeDiffView(this.exercise(), this.editableInstructions());
    }

    /**
     * Cancels the ongoing problem statement generation or refinement.
     * Resets all in-progress states.
     */
    cancelAiOperation(): void {
        this.aiOps.cancelAiOperation();
    }

    /**
     * Toggles the refinement prompt popover visibility.
     */
    toggleRefinementPopover(event: Event, target?: HTMLElement): void {
        if (this.isExerciseGenerationActionBlocked()) {
            this.openGenerationPage();
            return;
        }
        const origin = target ?? event.currentTarget;
        if (origin instanceof HTMLElement) {
            this.refinementPopover()?.toggle(origin);
        }
    }

    /**
     * Submits the full problem statement refinement.
     * Hides the popover, then delegates to the shared AI operations helper.
     */
    submitRefinement(): void {
        if (this.isExerciseGenerationActionBlocked()) return;
        const prompt = this.refinementPrompt().trim();
        if (!prompt || !this.exercise()) return;

        this.refinementPopover()?.close();
        this.aiOps.handleProblemStatementAction(this.exercise(), this.editableInstructions());
    }

    /**
     * Handles inline refinement request from editor selection.
     */
    onInlineRefinement(event: InlineRefinementEvent): void {
        if (this.isExerciseGenerationActionBlocked()) return;
        this.aiOps.onInlineRefinement(this.exercise(), this.editableInstructions(), event);
    }

    /**
     * Returns a Bootstrap text color class based on an issue's severity.
     *
     * @param {ConsistencyIssueSeverityEnum} severity
     *        The severity that determines the color.
     *
     * @returns
     *          A text color class (`text-danger`, `text-warning`, `text-info`, or `text-secondary`).
     */
    getSeverityColor(severity: ConsistencyIssueSeverityEnum | undefined) {
        switch (severity) {
            case 'HIGH':
                return 'text-danger';
            case 'MEDIUM':
                return 'text-warning';
            case 'LOW':
                return 'text-info';
            default:
                return 'text-secondary';
        }
    }

    readonly totalLocationsCount = computed(() => this.sortedIssues().length);
    readonly showConsistencyIssuesToolbar = signal(false);

    get currentGlobalIndex(): number {
        const issues = this.sortedIssues();
        if (!this.selectedIssue()) {
            return 0;
        }
        const index = issues.findIndex((issue) => issue.threadId === this.selectedIssue()?.threadId);
        return index >= 0 ? index + 1 : 0;
    }

    toggleConsistencyIssuesToolbar() {
        this.showConsistencyIssuesToolbar.update((v) => !v);
        if (this.showConsistencyIssuesToolbar()) {
            this.jumpToSelectedIssue();
        }
    }

    /** Moves the editor to whatever {@link selectedIssue} currently resolves to, if anything. */
    private jumpToSelectedIssue(): void {
        const issue = this.selectedIssue();
        if (issue) {
            this.jumpToLocation(issue);
        }
    }

    /**
     * Navigates through consistency issues globally.
     * @param {number} step - Direction to navigate (1 for next, -1 for previous).
     */
    navigateGlobal(step: number): void {
        const issues = this.sortedIssues();
        if (!issues.length) {
            return;
        }

        let currentIndex = -1;
        if (this.selectedIssue()) {
            currentIndex = issues.findIndex((issue) => issue.threadId === this.selectedIssue()?.threadId);
        }

        let newIndex = currentIndex + step;
        if (newIndex >= issues.length) {
            newIndex = 0;
        } else if (newIndex < 0) {
            newIndex = issues.length - 1;
        }

        this.selectedIssue.set(issues[newIndex]);
        this.jumpToLocation(this.selectedIssue()!);
    }

    /**
     * Navigates to a review-thread location emitted by review comment widgets.
     */
    onNavigateToReviewCommentLocation(location: ReviewThreadLocation): void {
        if (location.threadId !== undefined) {
            const selectedIssue = this.sortedIssues().find((issue) => issue.threadId === location.threadId);
            if (selectedIssue) {
                this.selectedIssue.set(selectedIssue);
            }
        }
        this.navigateToLocation(location);
    }

    /**
     * Helper to perform the actual editor jump.
     */
    private jumpToLocation(issue: ConsistencyIssueNavigationIssue) {
        this.navigateToLocation({
            targetType: issue.targetType,
            filePath: issue.filePath,
            lineNumber: issue.lineNumber,
            auxiliaryRepositoryId: issue.auxiliaryRepositoryId,
        });
    }

    private mapConsistencyThreadToNavigationIssue(thread: CommentThread): ConsistencyIssueNavigationIssue | undefined {
        const content = this.extractConsistencyIssueContent(thread);
        if (!content) {
            return undefined;
        }

        return {
            threadId: thread.id,
            targetType: thread.targetType,
            filePath: thread.filePath ?? thread.initialFilePath ?? undefined,
            lineNumber: thread.lineNumber ?? thread.initialLineNumber,
            auxiliaryRepositoryId: thread.auxiliaryRepositoryId,
            severity: content.severity,
            category: content.category,
        };
    }

    private extractConsistencyIssueContent(thread: CommentThread): ConsistencyIssueCommentContent | undefined {
        const firstComment = getFirstCommentByCreatedDateThenId(thread.comments);
        if (!firstComment || firstComment.type !== CommentType.CONSISTENCY_CHECK) {
            return undefined;
        }

        const content = firstComment.content as CommentContent | undefined;
        if (!content || content.contentType !== CommentContentType.CONSISTENCY_CHECK) {
            return undefined;
        }

        return content;
    }

    private navigateToLocation(location: { targetType: CommentThreadLocationType; filePath?: string; lineNumber?: number; auxiliaryRepositoryId?: number }): void {
        if (location.targetType === CommentThreadLocationType.PROBLEM_STATEMENT) {
            const codeEditorContainer = this.codeEditorContainer()!;
            codeEditorContainer.selectedFile = codeEditorContainer.problemStatementIdentifier;
            if (location.lineNumber !== undefined) {
                this.editableInstructions()?.jumpToLine(location.lineNumber);
            }
            return;
        }

        if (!location.filePath) {
            return;
        }

        this.lineJumpOnFileLoad = location.lineNumber;
        this.fileToJumpOn = location.filePath;
        this.repositorySwitchTarget = undefined;

        try {
            const codeEditorContainer = this.codeEditorContainer()!;
            switch (location.targetType) {
                case CommentThreadLocationType.TEMPLATE_REPO:
                    if (codeEditorContainer.selectedRepository() !== RepositoryType.TEMPLATE) {
                        this.repositorySwitchTarget = { repository: RepositoryType.TEMPLATE };
                        void this.selectTemplateParticipation();
                        return;
                    }
                    break;
                case CommentThreadLocationType.SOLUTION_REPO:
                    if (codeEditorContainer.selectedRepository() !== RepositoryType.SOLUTION) {
                        this.repositorySwitchTarget = { repository: RepositoryType.SOLUTION };
                        void this.selectSolutionParticipation();
                        return;
                    }
                    break;
                case CommentThreadLocationType.TEST_REPO:
                    if (codeEditorContainer.selectedRepository() !== RepositoryType.TESTS) {
                        this.repositorySwitchTarget = { repository: RepositoryType.TESTS };
                        void this.selectTestRepository();
                        return;
                    }
                    break;
                case CommentThreadLocationType.AUXILIARY_REPO: {
                    const auxiliaryRepositoryId = location.auxiliaryRepositoryId;
                    if (
                        auxiliaryRepositoryId !== undefined &&
                        (codeEditorContainer.selectedRepository() !== RepositoryType.AUXILIARY || this.selectedRepositoryId !== auxiliaryRepositoryId)
                    ) {
                        this.repositorySwitchTarget = { repository: RepositoryType.AUXILIARY, auxiliaryRepositoryId };
                        void this.selectAuxiliaryRepository(auxiliaryRepositoryId);
                        return;
                    }
                    break;
                }
                default:
            }
        } catch {
            this.alertService.error('artemisApp.hyperion.consistencyCheck.navigationFailed');
            this.repositorySwitchTarget = undefined;
            this.lineJumpOnFileLoad = undefined;
            this.fileToJumpOn = undefined;
            return;
        }

        // Trigger manual load if already in correct repo
        this.onEditorLoaded();
    }

    /**
     * Ensures the target file is loaded once the editor is ready.
     *
     * If the file is already selected (and no load event will fire),
     * the file-load handler is invoked directly. Otherwise, selecting
     * the file triggers the normal load workflow.
     */
    onEditorLoaded() {
        if (this.fileToJumpOn && !this.repositorySwitchTarget) {
            const codeEditorContainer = this.codeEditorContainer()!;
            // File already loaded, no file-load event will fire.
            // Jump directly without re-running file-sync load/rebind.
            if (codeEditorContainer.selectedFile === this.fileToJumpOn) {
                this.performDeferredLineJump(this.fileToJumpOn);
                return;
            }

            // Will load file and signal to fileLoad when finished loading
            codeEditorContainer.selectedFile = this.fileToJumpOn;
        }
    }

    onRepositoryFilesLoaded(): void {
        const target = this.repositorySwitchTarget;
        if (!target) {
            return;
        }

        const codeEditorContainer = this.codeEditorContainer()!;
        if (
            codeEditorContainer.selectedRepository() !== target.repository ||
            (target.repository === RepositoryType.AUXILIARY && this.selectedRepositoryId !== target.auxiliaryRepositoryId)
        ) {
            return;
        }

        this.repositorySwitchTarget = undefined;
        this.onEditorLoaded();
    }

    /**
     * Performs a deferred jump to a specific line after a file has finished loading.
     *
     * @param {string} fileName
     *        The name of the file that was just loaded.
     */
    onFileLoad(fileName: string) {
        this.onFileSyncLoad(fileName);
        this.performDeferredLineJump(fileName);
    }

    /**
     * Performs the pending line jump when the target file is currently active.
     *
     * @param fileName The file that is currently active/loaded.
     */
    private performDeferredLineJump(fileName: string): void {
        if (this.fileToJumpOn === fileName) {
            if (this.lineJumpOnFileLoad !== undefined) {
                this.codeEditorContainer()!.jumpToLine(this.lineJumpOnFileLoad);
            }
            this.lineJumpOnFileLoad = undefined;
            this.fileToJumpOn = undefined;
        }
    }

    onDiffLineChange(event: { ready: boolean; lineChange: LineChange }): void {
        this.aiOps.onDiffLineChange(event);
    }

    override onInstructionChanged(markdown: string) {
        super.onInstructionChanged(markdown);
        this.aiOps.currentProblemStatement.set(markdown);
    }
}
