import { Component, DestroyRef, Injector, OnDestroy, computed, effect, inject, signal, viewChild } from '@angular/core';
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
import { DomainChange, DomainType, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Observable, Subject, catchError, finalize, forkJoin, from, map, of, take, takeUntil, tap, timeout } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ProblemStatementAiOperationsHelper } from 'app/programming/manage/shared/problem-statement-ai-operations.helper';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ConfirmationService } from 'primeng/api';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/editor/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssueCategoryEnum, ConsistencyIssueSeverityEnum } from 'app/openapi/model/consistency-issue';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import {
    ReviewAdaptExerciseDialogComponent,
    ReviewAdaptExerciseDialogData,
    ReviewAdaptExerciseDialogResult,
} from 'app/exercise/review/adapt-exercise-dialog/review-adapt-exercise-dialog.component';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import dayjs from 'dayjs/esm';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContent, CommentContentType, ConsistencyIssueCommentContent } from 'app/exercise/shared/entities/review/comment-content.model';
import { CommentThread, CommentThreadLocationType, ReviewThreadLocation } from 'app/exercise/shared/entities/review/comment-thread.model';
import { firstConsistencyIssueContent, getFirstCommentByCreatedDateThenId, selectedThreadsFindings } from 'app/exercise/review/review-comment-utils';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { ProblemStatementService } from 'app/programming/manage/services/problem-statement.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { InlineRefinementEvent, MAX_USER_PROMPT_LENGTH } from 'app/programming/manage/shared/problem-statement.utils';
import { TooltipModule } from 'primeng/tooltip';
import { TextareaModule } from 'primeng/textarea';
import { BadgeModule } from 'primeng/badge';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { Popover, PopoverModule } from 'primeng/popover';
import {
    HyperionGenerationActivityComponent,
    HyperionGenerationCompletedEvent,
    HyperionReviewRequestedEvent,
} from 'app/hyperion/exercise-generation/hyperion-generation-activity.component';
import { ExerciseGenerationFileSnapshot, HyperionGenerationMode } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { Router } from '@angular/router';
import { supportsHyperionExerciseGeneration } from 'app/hyperion/exercise-generation/hyperion-generation-support';

const SEVERITY_ORDER: Record<ConsistencyIssueSeverityEnum, number> = {
    ['HIGH']: 0,
    ['MEDIUM']: 1,
    ['LOW']: 2,
};

const AUTO_START_EXERCISE_GENERATION_STATE = 'autoStartExerciseGeneration';
const MIN_MEANINGFUL_SPEC_LENGTH = 40;
const HYPERION_EDITOR_REFRESH_TIMEOUT_MS = 30_000;
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
    providers: [ExerciseReviewCommentService, ConfirmationService],
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
        TextareaModule,
        BadgeModule,
        ButtonModule,
        MessageModule,
        PopoverModule,
        HyperionGenerationActivityComponent,
        ConfirmDialogModule,
    ],
})
export class CodeEditorInstructorAndEditorContainerComponent extends CodeEditorInstructorBaseContainerComponent implements OnDestroy {
    readonly resultComp = viewChild(UpdatingResultComponent);
    readonly editableInstructions = viewChild(ProgrammingExerciseEditableInstructionComponent);
    private readonly generationActivity = viewChild(HyperionGenerationActivityComponent);

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

    readonly refinementPopover = viewChild<Popover>('refinementPopover');
    /** Prompt bound to the refinement popover textarea — aliased to aiOps.userPrompt. */
    readonly refinementPrompt = this.aiOps.userPrompt;
    protected readonly faPaperPlane = faPaperPlane;

    private consistencyCheckService = inject(ConsistencyCheckService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private exerciseReviewCommentService = inject(ExerciseReviewCommentService);

    lineJumpOnFileLoad: number | undefined = undefined;
    fileToJumpOn: string | undefined = undefined;
    private repositorySwitchTarget: { repository: RepositoryType; auxiliaryRepositoryId?: number } | undefined;
    readonly selectedIssue = signal<ConsistencyIssueNavigationIssue | undefined>(undefined);
    readonly generationStartPending = signal(false);
    readonly generationRefreshPending = signal(false);
    readonly generationRefreshFailed = signal(false);
    readonly generationRefreshBaselineUnknown = signal(false);
    private generationStartSequence = 0;
    private generationRefreshSequence = 0;
    private activeAdaptDialogRef?: DynamicDialogRef<ReviewAdaptExerciseDialogComponent>;
    private pendingGenerationRefreshCompletedAt?: number;
    private generationRefreshQueued = false;
    private readonly exerciseChanged = new Subject<void>();
    private shouldAutoStartExerciseGeneration = window.history.state?.[AUTO_START_EXERCISE_GENERATION_STATE] === true;

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
    private dialogService = inject(DialogService);
    private confirmationService = inject(ConfirmationService);
    private generationService = inject(HyperionExerciseGenerationService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private reviewRouter = inject(Router);
    private readonly editorDestroyRef = inject(DestroyRef);
    private readonly reviewRequestsInFlight = new Set<string>();
    private readonly selectedAdaptFeedbackThreads = computed(() =>
        this.exerciseReviewCommentService.selectedFeedbackThreads().filter((thread) => !thread.resolved && !thread.outdated && firstConsistencyIssueContent(thread)),
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
                if (this.exercise?.id && exercise?.id && this.exercise.id !== exercise.id) {
                    return; // Ignore stale async results from a different exercise
                }
                this.onInstructionChanged(content);
            },
        });
        effect(() => {
            if (!this.showConsistencyIssuesToolbar()) {
                return;
            }

            const issues = this.sortedIssues();
            if (!issues.length) {
                return;
            }

            const hasValidSelection = this.selectedIssue() ? issues.some((issue) => issue.threadId === this.selectedIssue()?.threadId) : false;
            if (hasValidSelection) {
                return;
            }

            this.selectedIssue.set(issues[0]);
            this.jumpToLocation(this.selectedIssue()!);
        });
        effect(() => {
            if (!this.shouldAutoStartExerciseGeneration) {
                return;
            }
            this.generationActivity()?.statusLoading();
            this.maybeAutoStartExerciseGenerationFromNavigation();
        });
    }

    override loadExercise(exerciseId: number): Observable<ProgrammingExercise> {
        if (this.exercise?.id !== undefined && this.exercise.id !== exerciseId) {
            this.invalidateHyperionLifecycleState();
        }
        return super.loadExercise(exerciseId).pipe(
            tap((exercise) => {
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
        this.editableInstructions()?.clearReviewCommentDrafts();
        this.exerciseReviewCommentService.reloadThreads();
    }

    protected onHyperionGenerationReverted(completedAt: string): void {
        this.refreshAfterHyperionRepositoryChange(Date.parse(completedAt));
    }

    protected onHyperionGenerationCompleted(event: HyperionGenerationCompletedEvent): void {
        if (event.completionStatus === 'NEEDS_REVIEW') {
            this.exerciseReviewCommentService.reloadThreads();
            return;
        }
        if (event.liveExerciseChanged === true) {
            this.refreshAfterHyperionRepositoryChange(event.completedAt ? Date.parse(event.completedAt) : undefined);
        }
    }

    protected onHyperionSnapshotSelected(snapshot: ExerciseGenerationFileSnapshot): void {
        if (!this.generationActivity()?.canNavigateSnapshots()) {
            return;
        }
        let targetType: CommentThreadLocationType;
        switch (snapshot.repo) {
            case 'solution':
                targetType = CommentThreadLocationType.SOLUTION_REPO;
                break;
            case 'template':
                targetType = CommentThreadLocationType.TEMPLATE_REPO;
                break;
            case 'tests':
                targetType = CommentThreadLocationType.TEST_REPO;
                break;
            case 'other':
                if (snapshot.path !== 'problem-statement.md') {
                    return;
                }
                targetType = CommentThreadLocationType.PROBLEM_STATEMENT;
                break;
        }
        const prefix = `${snapshot.repo}/`;
        const filePath = snapshot.path.startsWith(prefix) ? snapshot.path.slice(prefix.length) : snapshot.path;
        this.navigateToLocation({ targetType, filePath });
    }

    protected onHyperionReviewRequested(request: HyperionReviewRequestedEvent): void {
        const exerciseId = this.exercise?.id;
        const courseId = this.exercise?.course?.id;
        if (exerciseId === undefined || courseId === undefined) {
            this.alertService.error('artemisApp.hyperion.generationActivity.reviewUnavailable');
            return;
        }
        if (request.target === 'problem-statement') {
            const requestKey = `${request.jobId}:problem-statement`;
            if (this.reviewRequestsInFlight.has(requestKey)) {
                return;
            }
            this.reviewRequestsInFlight.add(requestKey);
            this.navigateToHyperionReview(['/course-management', courseId, 'programming-exercises', exerciseId, 'version-history'], requestKey);
            return;
        }

        const repositoryType = {
            solution: RepositoryType.SOLUTION,
            template: RepositoryType.TEMPLATE,
            tests: RepositoryType.TESTS,
        }[request.target];
        const requestKey = `${request.jobId}:${repositoryType}`;
        if (this.reviewRequestsInFlight.has(requestKey)) {
            return;
        }
        this.reviewRequestsInFlight.add(requestKey);
        let navigationStarted = false;
        this.programmingExerciseParticipationService
            .retrieveCommitHistoryForTemplateSolutionOrTests(exerciseId, repositoryType)
            .pipe(
                take(1),
                takeUntilDestroyed(this.editorDestroyRef),
                finalize(() => {
                    if (!navigationStarted) {
                        this.reviewRequestsInFlight.delete(requestKey);
                    }
                }),
            )
            .subscribe({
                next: (commits) => {
                    const expectedMessage = `Generate exercise with Hyperion (${request.jobId})`;
                    const matchingCommits = commits.filter((candidate) => candidate.hash && candidate.message === expectedMessage);
                    if (matchingCommits.length !== 1) {
                        this.alertService.error('artemisApp.hyperion.generationActivity.reviewUnavailable');
                        return;
                    }
                    navigationStarted = true;
                    this.navigateToHyperionReview(
                        ['/course-management', courseId, 'programming-exercises', exerciseId, 'repository', repositoryType, 'commit-history', matchingCommits[0].hash!],
                        requestKey,
                    );
                },
                error: () => this.alertService.error('artemisApp.hyperion.generationActivity.reviewUnavailable'),
            });
    }

    private navigateToHyperionReview(commands: unknown[], requestKey?: string): void {
        from(this.reviewRouter.navigate(commands))
            .pipe(
                take(1),
                takeUntilDestroyed(this.editorDestroyRef),
                finalize(() => {
                    if (requestKey) {
                        this.reviewRequestsInFlight.delete(requestKey);
                    }
                }),
            )
            .subscribe({
                next: (navigated) => {
                    if (!navigated) {
                        this.alertService.error('artemisApp.hyperion.generationActivity.reviewUnavailable');
                    }
                },
                error: () => this.alertService.error('artemisApp.hyperion.generationActivity.reviewUnavailable'),
            });
    }

    protected openHyperionPanel(): void {
        this.codeEditorContainer()?.openEditorBottomPanel();
    }

    protected onAiToolbarClick(event: Event, popover: Popover): void {
        if (this.isExerciseGenerationActionBlocked()) {
            this.openHyperionPanel();
            return;
        }
        popover.toggle(event);
    }

    private refreshAfterHyperionRepositoryChange(operationCompletedAt?: number): void {
        const exerciseId = this.exercise?.id;
        if (exerciseId === undefined) {
            return;
        }
        if (operationCompletedAt !== undefined) {
            this.pendingGenerationRefreshCompletedAt = operationCompletedAt;
        }
        if (this.generationRefreshPending()) {
            this.generationRefreshQueued = true;
            return;
        }
        if (!this.canRefreshAfterHyperionRepositoryChange()) {
            this.generationRefreshFailed.set(true);
            this.alertService.warning('pendingChanges');
            return;
        }
        const refreshSequence = ++this.generationRefreshSequence;
        const actions = this.codeEditorContainer()?.actions();
        this.generationRefreshFailed.set(false);
        this.generationRefreshBaselineUnknown.set(false);
        this.generationRefreshPending.set(true);
        this.fileSyncService.beginExpectedRepositoryUpdate(this.pendingGenerationRefreshCompletedAt);

        const repositoryRefresh = actions
            ? actions.refreshAfterExternalUpdate(this.currentRepositoryDomain()).pipe(
                  timeout(HYPERION_EDITOR_REFRESH_TIMEOUT_MS),
                  catchError(() => of(false)),
              )
            : of(true);
        const exerciseRefresh = this.reloadExerciseFromServer(exerciseId).pipe(
            timeout(HYPERION_EDITOR_REFRESH_TIMEOUT_MS),
            catchError(() => {
                this.alertService.error('artemisApp.editor.errors.refreshFailed', { connectionIssue: '' });
                return of(undefined);
            }),
        );

        forkJoin({ repositoryRefresh, exerciseRefresh })
            .pipe(
                take(1),
                takeUntil(this.exerciseChanged),
                takeUntilDestroyed(this.editorDestroyRef),
                finalize(() => this.finishHyperionRefresh(refreshSequence)),
            )
            .subscribe({
                next: ({ repositoryRefresh: repositoryRefreshSucceeded, exerciseRefresh: refreshedExercise }) => {
                    if (refreshSequence !== this.generationRefreshSequence || this.exercise?.id !== exerciseId) {
                        return;
                    }
                    const safeToApply = this.canRefreshAfterHyperionRepositoryChange();
                    if (repositoryRefreshSucceeded && refreshedExercise && safeToApply) {
                        this.editableInstructions()?.acceptServerBaseline(refreshedExercise);
                        this.exercise = refreshedExercise;
                        this.onInstructionChanged(refreshedExercise.problemStatement ?? '');
                    }
                    const refreshFailed = !repositoryRefreshSucceeded || refreshedExercise === undefined || !safeToApply;
                    this.generationRefreshFailed.set(refreshFailed);
                    this.generationRefreshBaselineUnknown.set(refreshFailed);
                },
            });
    }

    private currentRepositoryDomain(): DomainChange | undefined {
        switch (this.selectedRepository) {
            case RepositoryType.TEMPLATE:
                return this.exercise.templateParticipation ? [DomainType.PARTICIPATION, this.exercise.templateParticipation] : undefined;
            case RepositoryType.SOLUTION:
                return this.exercise.solutionParticipation ? [DomainType.PARTICIPATION, this.exercise.solutionParticipation] : undefined;
            case RepositoryType.TESTS:
                return [DomainType.TEST_REPOSITORY, this.exercise];
            case RepositoryType.AUXILIARY: {
                const repository = this.exercise.auxiliaryRepositories?.find((candidate) => candidate.id === this.selectedRepositoryId);
                return repository ? [DomainType.AUXILIARY_REPOSITORY, repository] : undefined;
            }
            default:
                return this.selectedParticipation ? [DomainType.PARTICIPATION, this.selectedParticipation] : undefined;
        }
    }

    protected retryHyperionRefresh(): void {
        this.refreshAfterHyperionRepositoryChange(this.pendingGenerationRefreshCompletedAt);
    }

    private finishHyperionRefresh(refreshSequence: number): void {
        if (refreshSequence !== this.generationRefreshSequence) {
            return;
        }
        this.fileSyncService.endExpectedRepositoryUpdate();
        this.generationRefreshPending.set(false);
        if (this.generationRefreshQueued) {
            this.generationRefreshQueued = false;
            this.refreshAfterHyperionRepositoryChange(this.pendingGenerationRefreshCompletedAt);
        } else if (!this.generationRefreshFailed()) {
            this.pendingGenerationRefreshCompletedAt = undefined;
        }
    }

    private canRefreshAfterHyperionRepositoryChange(): boolean {
        const codeEditorClean = this.codeEditorContainer()?.canDeactivate?.() ?? true;
        const problemStatementClean = !(this.editableInstructions()?.unsavedChangesValue?.() ?? false);
        return codeEditorClean && problemStatementClean;
    }

    private reloadExerciseFromServer(exerciseId: number): Observable<ProgrammingExercise> {
        return this.programmingExerciseService.findWithTemplateAndSolutionParticipationAndResults(exerciseId).pipe(
            map(({ body }) => {
                if (body?.id !== exerciseId || body.templateParticipation?.id === undefined || body.solutionParticipation?.id === undefined) {
                    throw new Error('The refreshed programming exercise is missing its setup participations.');
                }
                return body;
            }),
        );
    }

    protected startGeneration(skipConfirmation = false): void {
        const exerciseId = this.exercise?.id;
        if (exerciseId === undefined || !this.canGenerateExercise() || this.isExerciseGenerationActionBlocked()) {
            return;
        }
        if ((this.exercise.problemStatement?.trim().length ?? 0) < MIN_MEANINGFUL_SPEC_LENGTH) {
            this.alertService.warning('artemisApp.hyperion.generationActivity.meaningfulSpecRequired');
            return;
        }
        if (!this.canRefreshAfterHyperionRepositoryChange()) {
            this.alertService.warning('pendingChanges');
            return;
        }
        if (!skipConfirmation) {
            this.confirmationService.confirm({
                key: 'hyperionGenerateConfirmation',
                header: this.translateService.instant('artemisApp.hyperion.generationActivity.generateConfirmHeader'),
                message: this.translateService.instant('artemisApp.hyperion.generationActivity.generateConfirmMessage'),
                rejectButtonProps: {
                    label: this.translateService.instant('entity.action.cancel'),
                    severity: 'secondary',
                },
                acceptButtonProps: {
                    label: this.translateService.instant('artemisApp.programmingExercise.codeGeneration.generateCode'),
                },
                defaultFocus: 'reject',
                accept: () => this.dispatchGeneration(exerciseId),
            });
            return;
        }
        this.dispatchGeneration(exerciseId);
    }

    private dispatchGeneration(exerciseId: number): void {
        if (this.editorDestroyRef.destroyed || this.exercise?.id !== exerciseId || this.isExerciseGenerationActionBlocked()) {
            return;
        }
        if (!this.canRefreshAfterHyperionRepositoryChange()) {
            this.alertService.warning('pendingChanges');
            return;
        }
        const requestSequence = ++this.generationStartSequence;
        this.generationStartPending.set(true);
        this.generationService
            .generate(exerciseId, { mode: 'GENERATE' })
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
                    if (requestSequence !== this.generationStartSequence || this.exercise?.id !== exerciseId) {
                        return;
                    }
                    this.generationActivity()?.attachToJob(jobId, 'GENERATE');
                    this.openHyperionPanel();
                },
                error: () => {
                    if (requestSequence === this.generationStartSequence && this.exercise?.id === exerciseId) {
                        this.alertService.error('artemisApp.hyperion.generationActivity.startFailed');
                    }
                },
            });
    }

    protected canGenerateExercise(): boolean {
        const exercise = this.exercise;
        if (!supportsHyperionExerciseGeneration(exercise?.programmingLanguage, exercise?.projectType)) {
            return false;
        }
        const isReleased = exercise.releaseDate === undefined || !dayjs(exercise.releaseDate).isAfter(dayjs());
        const studentParticipationCount = exercise.studentParticipations?.length ?? exercise.numberOfParticipations ?? 0;
        return !isReleased && studentParticipationCount === 0;
    }

    protected isExerciseGenerationRunning(): boolean {
        const activity = this.generationActivity();
        return this.generationStartPending() || this.generationRefreshPending() || (this.showGenerationActivity() && (activity?.statusLoading() || activity?.running() || false));
    }

    protected isExerciseGenerationActionBlocked(): boolean {
        const activity = this.generationActivity();
        return this.isExerciseGenerationRunning() || this.generationRefreshFailed() || (this.showGenerationActivity() && (activity === undefined || activity.statusLoadFailed()));
    }

    protected isProblemStatementEditingLocked(): boolean {
        const activity = this.generationActivity();
        return (
            this.isExerciseGenerationRunning() ||
            this.generationRefreshBaselineUnknown() ||
            (this.showGenerationActivity() && (activity === undefined || activity.statusLoadFailed()))
        );
    }

    protected showGenerationActivity(): boolean {
        return (
            this.hyperionGenerationSupported &&
            !!this.exercise?.id &&
            (this.exercise?.isAtLeastEditor ?? false) &&
            supportsHyperionExerciseGeneration(this.exercise?.programmingLanguage, this.exercise?.projectType)
        );
    }

    protected canAdaptWithFeedback(): boolean {
        return this.showGenerationActivity() && this.canGenerateExercise();
    }

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
        const exerciseId = this.exercise?.id;
        if (exerciseId === undefined) {
            return;
        }
        if (!this.canRefreshAfterHyperionRepositoryChange()) {
            onCancel?.();
            this.alertService.warning('pendingChanges');
            return;
        }
        const findings = selectedThreadsFindings(this.selectedAdaptFeedbackThreads(), this.translateService);
        const dialogRef = this.dialogService.open(ReviewAdaptExerciseDialogComponent, {
            header: this.translateService.instant('artemisApp.review.adaptExercise.title'),
            modal: true,
            dismissableMask: true,
            width: '40rem',
            breakpoints: { '640px': 'calc(100vw - 2rem)' },
            data: { findings } satisfies ReviewAdaptExerciseDialogData,
        });
        if (!dialogRef) {
            return;
        }
        this.activeAdaptDialogRef = dialogRef;
        dialogRef.onClose
            .pipe(
                take(1),
                takeUntil(this.exerciseChanged),
                takeUntilDestroyed(this.editorDestroyRef),
                finalize(() => {
                    if (this.activeAdaptDialogRef === dialogRef) {
                        this.activeAdaptDialogRef = undefined;
                    }
                }),
            )
            .subscribe((result?: ReviewAdaptExerciseDialogResult) => {
                if (this.exercise?.id !== exerciseId) {
                    return;
                }
                if (!result) {
                    onCancel?.();
                    return;
                }
                this.startAdaptation(result.instructions);
            });
    }

    private startAdaptation(instructions?: string): void {
        const exerciseId = this.exercise?.id;
        if (exerciseId === undefined || this.isExerciseGenerationActionBlocked()) {
            return;
        }
        if (!this.canRefreshAfterHyperionRepositoryChange()) {
            this.alertService.warning('pendingChanges');
            return;
        }
        const selectedFeedbackThreadIds = this.selectedAdaptFeedbackThreadIds();
        const requestSequence = ++this.generationStartSequence;
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
                    if (requestSequence !== this.generationStartSequence || this.exercise?.id !== exerciseId) {
                        return;
                    }
                    this.exerciseReviewCommentService.clearSelectedFeedback();
                    this.generationActivity()?.attachToJob(jobId, 'ADAPT');
                    this.openHyperionPanel();
                },
                error: () => {
                    if (requestSequence === this.generationStartSequence && this.exercise?.id === exerciseId) {
                        this.alertService.error('artemisApp.hyperion.generationActivity.adaptStartFailed');
                    }
                },
            });
    }

    override selectTemplateParticipation(): Promise<boolean> {
        return this.isExerciseGenerationActionBlocked() ? Promise.resolve(false) : super.selectTemplateParticipation();
    }

    override selectSolutionParticipation(): Promise<boolean> {
        return this.isExerciseGenerationActionBlocked() ? Promise.resolve(false) : super.selectSolutionParticipation();
    }

    override selectAssignmentParticipation(): Promise<boolean> {
        return this.isExerciseGenerationActionBlocked() ? Promise.resolve(false) : super.selectAssignmentParticipation();
    }

    override selectTestRepository(): Promise<boolean> {
        return this.isExerciseGenerationActionBlocked() ? Promise.resolve(false) : super.selectTestRepository();
    }

    override selectAuxiliaryRepository(repositoryId: number): Promise<boolean> {
        return this.isExerciseGenerationActionBlocked() ? Promise.resolve(false) : super.selectAuxiliaryRepository(repositoryId);
    }

    /**
     * Updates repository-specific generation state when the user switches domains in the editor.
     *
     * Restores a running generation only when no local generation queue is currently active.
     */
    protected override applyDomainChange(domainType: DomainChange[0], domainValue: DomainChange[1]) {
        super.applyDomainChange(domainType, domainValue);
        this.maybeAutoStartExerciseGenerationFromNavigation();
    }

    private maybeAutoStartExerciseGenerationFromNavigation(): void {
        if (!this.shouldAutoStartExerciseGeneration || !this.exercise?.id || !this.canGenerateExercise() || this.isExerciseGenerationActionBlocked()) {
            return;
        }

        this.shouldAutoStartExerciseGeneration = false;
        const updatedState = typeof window.history.state === 'object' && window.history.state !== null ? window.history.state : {};
        updatedState[AUTO_START_EXERCISE_GENERATION_STATE] = false;
        window.history.replaceState(updatedState, '');
        this.startGeneration(true);
    }

    protected onHyperionStartRequested(mode?: HyperionGenerationMode): void {
        if (mode === 'ADAPT') {
            this.openAdaptDialog();
        } else {
            this.startGeneration();
        }
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
        this.generationRefreshSequence++;
        this.closeHyperionOverlays();
        this.exerciseChanged.next();
        if (this.generationRefreshPending()) {
            this.fileSyncService.endExpectedRepositoryUpdate();
        }
        this.generationStartPending.set(false);
        this.generationRefreshPending.set(false);
        this.generationRefreshFailed.set(false);
        this.generationRefreshBaselineUnknown.set(false);
        this.generationRefreshQueued = false;
        this.pendingGenerationRefreshCompletedAt = undefined;
    }

    private closeHyperionOverlays(): void {
        this.confirmationService.close();
        this.activeAdaptDialogRef?.close();
        this.activeAdaptDialogRef = undefined;
        this.refinementPopover()?.hide();
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
            this.openHyperionPanel();
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
        this.aiOps.revertAllChanges(this.exercise, this.editableInstructions());
    }

    /**
     * Closes the diff view after syncing the current editor content to the model.
     */
    closeDiff(): void {
        this.aiOps.closeDiffView(this.exercise, this.editableInstructions());
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
            this.openHyperionPanel();
            return;
        }
        this.refinementPopover()?.toggle(event, target);
    }

    /**
     * Submits the full problem statement refinement.
     * Hides the popover, then delegates to the shared AI operations helper.
     */
    submitRefinement(): void {
        if (this.isExerciseGenerationActionBlocked()) return;
        const prompt = this.refinementPrompt().trim();
        if (!prompt || !this.exercise) return;

        this.refinementPopover()?.hide();
        this.aiOps.handleProblemStatementAction(this.exercise, this.editableInstructions());
    }

    /**
     * Handles inline refinement request from editor selection.
     */
    onInlineRefinement(event: InlineRefinementEvent): void {
        if (this.isExerciseGenerationActionBlocked()) return;
        this.aiOps.onInlineRefinement(this.exercise, this.editableInstructions(), event);
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
        const issues = this.sortedIssues();

        if (this.showConsistencyIssuesToolbar()) {
            const isIssueValid = this.selectedIssue() && issues.some((issue) => issue.threadId === this.selectedIssue()?.threadId);
            if (!isIssueValid && issues.length > 0) {
                this.selectedIssue.set(issues[0]);
                this.jumpToLocation(this.selectedIssue()!);
            }
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
