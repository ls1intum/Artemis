import { Component, DestroyRef, Injector, OnDestroy, TemplateRef, ViewChild, computed, effect, inject, signal, viewChild } from '@angular/core';
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
    faGear,
    faPaperPlane,
    faPlus,
    faSave,
    faSpinner,
    faTableColumns,
    faTimes,
    faTimesCircle,
    faTriangleExclamation,
} from '@fortawesome/free-solid-svg-icons';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from '../../status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CodeGenerationRequest } from 'app/openapi/model/codeGenerationRequest';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { HyperionCompletionStatus, HyperionEvent, HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { Observable, Subscription, catchError, of, take, tap } from 'rxjs';
import { ProblemStatementAiOperationsHelper } from 'app/programming/manage/shared/problem-statement-ai-operations.helper';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContent, CommentContentType, ConsistencyIssueCommentContent } from 'app/exercise/shared/entities/review/comment-content.model';
import { CommentThread, CommentThreadLocationType, ReviewThreadLocation } from 'app/exercise/shared/entities/review/comment-thread.model';
import { getFirstCommentByCreatedDateThenId } from 'app/exercise/review/review-comment-utils';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { ProblemStatementService } from 'app/programming/manage/services/problem-statement.service';
import { InlineRefinementEvent, MAX_USER_PROMPT_LENGTH } from 'app/programming/manage/shared/problem-statement.utils';
import { TooltipModule } from 'primeng/tooltip';
import { TextareaModule } from 'primeng/textarea';
import { BadgeModule } from 'primeng/badge';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { Popover, PopoverModule } from 'primeng/popover';

const SEVERITY_ORDER: Record<ConsistencyIssue.SeverityEnum, number> = {
    [ConsistencyIssue.SeverityEnum.High]: 0,
    [ConsistencyIssue.SeverityEnum.Medium]: 1,
    [ConsistencyIssue.SeverityEnum.Low]: 2,
};

const SUPPORTED_CODE_GENERATION_REPOSITORIES = [RepositoryType.SOLUTION, RepositoryType.TEMPLATE, RepositoryType.TESTS] as const;
const CODE_GENERATION_SLOT_RELEASE_POLL_INTERVAL_MS = 1000;
const CODE_GENERATION_SLOT_RELEASE_MAX_POLLS = 120;
const CODE_GENERATION_FILE_PULL_DEBOUNCE_MS = 250;
const CODE_GENERATION_STATUS_POPOVER_CENTER_OFFSET_PX = -10;

type SupportedCodeGenerationRepositoryType = (typeof SUPPORTED_CODE_GENERATION_REPOSITORIES)[number];
type CodeGenerationExecutionState = 'idle' | 'queued' | 'running' | 'success' | 'warning' | 'error' | 'skipped';
type CodeGenerationFileEventType = 'FILE_UPDATED' | 'NEW_FILE';
type CodeGenerationRepositoryTranslationKey = `artemisApp.programmingExercise.codeGeneration.repositories.${Lowercase<SupportedCodeGenerationRepositoryType>}`;
type CodeGenerationStateTranslationKey = `artemisApp.programmingExercise.codeGeneration.status.${CodeGenerationExecutionState}`;
type CodeGenerationFileActionTranslationKey = `artemisApp.programmingExercise.codeGeneration.status.${'fileCreated' | 'fileUpdated'}`;

const CODE_GENERATION_STATE_CLASSES: Record<CodeGenerationExecutionState, string> = {
    idle: 'text-body-secondary',
    queued: 'text-warning',
    running: 'text-primary',
    success: 'text-success',
    warning: 'text-warning',
    error: 'text-danger',
    skipped: 'text-muted',
};

const CODE_GENERATION_FILE_ACTION_TRANSLATION_KEYS: Record<CodeGenerationFileEventType, CodeGenerationFileActionTranslationKey> = {
    FILE_UPDATED: 'artemisApp.programmingExercise.codeGeneration.status.fileUpdated',
    NEW_FILE: 'artemisApp.programmingExercise.codeGeneration.status.fileCreated',
};

interface CodeGenerationFileActivity {
    repositoryType: SupportedCodeGenerationRepositoryType;
    eventType: CodeGenerationFileEventType;
    path: string;
    iteration?: number;
    timestamp: number;
}

interface CodeGenerationIterationActivityGroup {
    iteration?: number;
    activities: CodeGenerationFileActivity[];
}

interface CodeGenerationSelectedFeedbackThread {
    threadId: number;
    targetType: CommentThreadLocationType;
    auxiliaryRepositoryId?: number;
    filePath?: string;
    lineNumber?: number;
    locationLabel: string;
}

interface CodeGenerationSelectedFeedbackRepositorySummary {
    repositoryType: SupportedCodeGenerationRepositoryType;
    threadCount: number;
    threads: CodeGenerationSelectedFeedbackThread[];
}

interface CodeGenerationRepositoryStatus {
    repositoryType: SupportedCodeGenerationRepositoryType;
    enabled: boolean;
    state: CodeGenerationExecutionState;
    attempts?: number;
    message?: string;
    fileActivities: CodeGenerationFileActivity[];
}

interface ConsistencyIssueNavigationIssue {
    threadId: number;
    targetType: CommentThreadLocationType;
    filePath?: string;
    lineNumber?: number;
    auxiliaryRepositoryId?: number;
    severity: ConsistencyIssue.SeverityEnum;
    category: ConsistencyIssue.CategoryEnum;
}

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-and-editor-container.component.html',
    styleUrl: 'code-editor-instructor-and-editor-container.scss',
    // Keep review comment state scoped to each editor container instance.
    providers: [ExerciseReviewCommentService],
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
        CheckboxModule,
        MessageModule,
        PopoverModule,
    ],
})
export class CodeEditorInstructorAndEditorContainerComponent extends CodeEditorInstructorBaseContainerComponent implements OnDestroy {
    @ViewChild('codeGenerationRunningModal', { static: true }) codeGenerationRunningModal: TemplateRef<unknown>;
    readonly resultComp = viewChild(UpdatingResultComponent);
    readonly editableInstructions = viewChild(ProgrammingExerciseEditableInstructionComponent);

    readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly MAX_USER_PROMPT_LENGTH = MAX_USER_PROMPT_LENGTH;
    readonly MarkdownEditorHeight = MarkdownEditorHeight;
    readonly sortedIssues = computed(() =>
        this.exerciseReviewCommentService
            .threads()
            .filter((thread) => thread.resolved !== true)
            .map((thread) => this.mapConsistencyThreadToNavigationIssue(thread))
            .filter((issue): issue is ConsistencyIssueNavigationIssue => issue !== undefined)
            .sort(
                (a, b) =>
                    (SEVERITY_ORDER[a.severity] ?? SEVERITY_ORDER[ConsistencyIssue.SeverityEnum.Medium]) -
                        (SEVERITY_ORDER[b.severity] ?? SEVERITY_ORDER[ConsistencyIssue.SeverityEnum.Medium]) || a.threadId - b.threadId,
            ),
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
    protected readonly isPromptNearLimit = this.aiOps.isPromptNearLimit;
    readonly shouldShowGenerateButton = this.aiOps.shouldShowGenerateButton;

    readonly faTableColumns = faTableColumns;
    readonly ButtonSize = ButtonSize;

    readonly refinementPopover = viewChild<Popover>('refinementPopover');
    readonly codeGenerationSettingsPopover = viewChild<Popover>('codeGenerationSettingsPopover');
    readonly codeGenerationStatusPopover = viewChild<Popover>('codeGenerationStatusPopover');
    /** Prompt bound to the refinement popover textarea — aliased to aiOps.userPrompt. */
    readonly refinementPrompt = this.aiOps.userPrompt;
    protected readonly faPaperPlane = faPaperPlane;

    private consistencyCheckService = inject(ConsistencyCheckService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private exerciseReviewCommentService = inject(ExerciseReviewCommentService);

    lineJumpOnFileLoad: number | undefined = undefined;
    fileToJumpOn: string | undefined = undefined;
    selectedIssue: ConsistencyIssueNavigationIssue | undefined = undefined;

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
    protected readonly faGear = faGear;

    protected readonly faSpinner = faSpinner;
    protected readonly facArtemisIntelligence = facArtemisIntelligence;

    protected readonly RepositoryType = RepositoryType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faCheckDouble = faCheckDouble;
    private codeGenAlertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private hyperionWs = inject(HyperionWebsocketService);
    private repoService = inject(CodeEditorRepositoryService);
    private hyperionCodeGenerationApi = inject(HyperionCodeGenerationApiService);
    isGeneratingCode = signal(false);
    private jobSubscription?: Subscription;
    private jobTimeoutHandle?: number;
    private activeJobId?: string;
    private statusSubscription?: Subscription;
    private restoreRequestId = 0;
    private slotReleasePollTimeoutHandle?: number;
    private codeGenerationPullTimeoutHandles = new Map<SupportedCodeGenerationRepositoryType, number>();
    private codeGenerationPullSubscriptions = new Map<SupportedCodeGenerationRepositoryType, Subscription>();
    private repositoriesWithPendingCodeGenerationPull = new Set<SupportedCodeGenerationRepositoryType>();
    private repositoriesWithInFlightCodeGenerationPull = new Set<SupportedCodeGenerationRepositoryType>();
    private queuedCodeGenerationRepositories: SupportedCodeGenerationRepositoryType[] = [];
    private activeCodeGenerationRepository?: SupportedCodeGenerationRepositoryType;
    private hasCustomCodeGenerationSelection = false;
    readonly supportedCodeGenerationRepositories = SUPPORTED_CODE_GENERATION_REPOSITORIES;
    readonly codeGenerationStatuses = signal<CodeGenerationRepositoryStatus[]>(
        SUPPORTED_CODE_GENERATION_REPOSITORIES.map((repositoryType) => this.createCodeGenerationStatus(repositoryType)),
    );
    readonly expandedFeedbackSummaryRepositories = signal<SupportedCodeGenerationRepositoryType[]>([]);
    readonly codeGenerationActivityLog = computed(() =>
        this.codeGenerationStatuses()
            .flatMap((status) => status.fileActivities)
            .sort((left, right) => right.timestamp - left.timestamp),
    );
    readonly codeGenerationSelectedFeedbackSummaries = computed<CodeGenerationSelectedFeedbackRepositorySummary[]>(() => {
        const threadsById = new Map(this.exerciseReviewCommentService.threads().map((thread) => [thread.id, thread]));

        return this.supportedCodeGenerationRepositories.map((repositoryType) => {
            const selectedThreadIds = this.exerciseReviewCommentService.getSelectedFeedbackThreadIdsForRepository(repositoryType);
            const threads = selectedThreadIds
                .map((threadId) => threadsById.get(threadId))
                .filter((thread): thread is CommentThread => thread !== undefined)
                .map((thread) => this.mapThreadToSelectedFeedbackThread(thread));

            return {
                repositoryType,
                threadCount: threads.length,
                threads,
            };
        });
    });
    readonly totalSelectedFeedbackThreadCount = computed(() =>
        this.codeGenerationSelectedFeedbackSummaries().reduce((threadCount, summary) => threadCount + summary.threadCount, 0),
    );

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

            const hasValidSelection = this.selectedIssue ? issues.some((issue) => issue.threadId === this.selectedIssue?.threadId) : false;
            if (hasValidSelection) {
                return;
            }

            this.selectedIssue = issues[0];
            this.jumpToLocation(this.selectedIssue);
        });
    }

    override loadExercise(exerciseId: number): Observable<ProgrammingExercise> {
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
        this.codeEditorContainer?.monacoEditor?.clearReviewCommentDrafts();
        this.exerciseReviewCommentService.reloadThreads();
    }

    /**
     * Clears problem-statement draft widgets and reloads review comment threads after saving.
     */
    onProblemStatementSaved(): void {
        this.editableInstructions()?.clearReviewCommentDrafts();
        this.exerciseReviewCommentService.reloadThreads();
    }

    /**
     * Starts Hyperion code generation after user confirmation.
     */
    generateCode(): void {
        if (!this.exercise?.id || this.isGeneratingCode()) {
            return;
        }

        const repositories = this.getSelectedCodeGenerationRepositories();
        if (!repositories.length) {
            this.codeGenAlertService.addAlert({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.noRepositorySelected' });
            return;
        }
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
        modalRef.componentInstance.title = 'artemisApp.programmingExercise.codeGeneration.confirmTitle';
        modalRef.componentInstance.text = 'artemisApp.programmingExercise.codeGeneration.confirmText';
        modalRef.componentInstance.translateText = true;
        modalRef.result.then(() => this.startCodeGeneration(repositories)).catch(() => {});
    }

    /**
     * Triggers the async generation endpoint and subscribes to job updates.
     */
    private startCodeGeneration(repositories: SupportedCodeGenerationRepositoryType[]) {
        this.isGeneratingCode.set(true);
        this.restoreRequestId += 1;
        this.clearCodeGenerationStatusSubscription();
        this.clearSlotReleasePoll();
        this.codeGenerationSettingsPopover()?.hide();
        this.initializeCodeGenerationRunStatuses(repositories);
        this.queuedCodeGenerationRepositories = [...repositories];
        this.activeCodeGenerationRepository = undefined;
        this.runNextCodeGeneration();
    }

    /**
     * Starts the next queued repository generation request or finishes the queue when none remain.
     */
    private runNextCodeGeneration() {
        const repositoryType = this.queuedCodeGenerationRepositories.shift();
        if (!repositoryType || !this.exercise?.id) {
            this.activeCodeGenerationRepository = undefined;
            this.clearJobSubscription(true);
            return;
        }

        this.activeCodeGenerationRepository = repositoryType;
        this.updateCodeGenerationStatus(repositoryType, (status) => ({
            ...status,
            state: 'running',
            attempts: undefined,
            message: undefined,
        }));

        const request = this.createCodeGenerationRequest(repositoryType);
        const exerciseId = this.exercise.id;
        this.hyperionCodeGenerationApi.generateCode(exerciseId, request).subscribe({
            next: (res) => {
                if (!res?.jobId) {
                    this.stopCodeGenerationQueue(repositoryType, undefined, 'artemisApp.programmingExercise.codeGeneration.error');
                    return;
                }
                this.subscribeToJob(res.jobId, repositoryType);
            },
            error: (error: HttpErrorResponse) => {
                if (this.isCodeGenerationAlreadyRunning(error)) {
                    this.stopCodeGenerationQueue(
                        repositoryType,
                        this.translateService.instant('artemisApp.programmingExercise.codeGeneration.runningText'),
                        'artemisApp.programmingExercise.codeGeneration.error',
                        false,
                    );
                    this.openCodeGenerationRunningModal();
                    return;
                }
                this.stopCodeGenerationQueue(repositoryType, undefined, 'artemisApp.programmingExercise.codeGeneration.error');
            },
            complete: () => {},
        });
    }

    private isCodeGenerationAlreadyRunning(error: HttpErrorResponse): boolean {
        if (!error || error.status !== 409) {
            return false;
        }
        const payload = typeof error.error === 'object' && error.error !== null ? (error.error as Record<string, unknown>) : {};
        const errorKey =
            payload['errorKey'] ?? payload['X-artemisApp-error'] ?? payload['message'] ?? error.headers?.get('X-artemisApp-error') ?? error.headers?.get('X-artemisApp-message');
        return errorKey === 'codeGenerationRunning' || errorKey === 'error.codeGenerationRunning';
    }

    /**
     * Opens the modal that informs the user another code generation run is already active.
     */
    private openCodeGenerationRunningModal(): void {
        this.modalService.open(this.codeGenerationRunningModal, { backdrop: 'static', keyboard: false, size: 'md' });
    }

    /**
     * Updates repository-specific generation state when the user switches domains in the editor.
     *
     * Restores a running generation only when no local generation queue is currently active.
     */
    protected override applyDomainChange(domainType: any, domainValue: any) {
        super.applyDomainChange(domainType, domainValue);
        if (!this.hasCustomCodeGenerationSelection && !this.isGeneratingCode()) {
            this.syncCodeGenerationSelectionWithSelectedRepository();
        }
        this.restoreCodeGenerationState();
    }

    /**
     * Cleans up active code generation subscriptions, timers, and AI resources on component teardown.
     */
    override ngOnDestroy() {
        this.clearJobSubscription(true);
        this.clearCodeGenerationStatusSubscription();
        this.clearSlotReleasePoll();
        this.aiOps.destroy();
        super.ngOnDestroy();
    }

    /**
     * Restores an already running Hyperion generation job for the current exercise.
     *
     * The restore check is skipped while this component is already driving an active generation queue,
     * so tab switches do not cancel the queue's slot-release polling.
     */
    private restoreCodeGenerationState() {
        this.restoreRequestId += 1;

        if (!this.hyperionEnabled || !this.exercise?.id) {
            return;
        }
        if (this.isGeneratingCode()) {
            return;
        }
        this.clearCodeGenerationStatusSubscription();
        const request = this.createCheckOnlyCodeGenerationRequest();
        const requestId = this.restoreRequestId;
        this.statusSubscription = this.hyperionCodeGenerationApi.generateCode(this.exercise.id, request).subscribe({
            next: (res) => {
                if (requestId !== this.restoreRequestId) {
                    return;
                }
                if (res?.jobId) {
                    const repositoryType = res.repositoryType ? this.mapRepositoryTypeToCodeGenerationRequest(res.repositoryType) : undefined;
                    if (!repositoryType) {
                        this.clearJobSubscription(true);
                        return;
                    }
                    this.initializeCodeGenerationRunStatuses([repositoryType]);
                    this.activeCodeGenerationRepository = repositoryType;
                    this.updateCodeGenerationStatus(repositoryType, (status) => ({ ...status, state: 'running' }));
                    this.subscribeToJob(res.jobId, repositoryType);
                } else {
                    this.clearJobSubscription(true);
                }
            },
            error: () => {
                if (requestId !== this.restoreRequestId) {
                    return;
                }
                this.clearJobSubscription(true);
            },
        });
    }

    /**
     * Maps repository tabs that support generation to the repository type expected by the code generation request.
     * @param repositoryType currently selected repository in the editor
     * @returns the matching supported generation repository, or `undefined` for unsupported tabs
     */
    private mapRepositoryTypeToCodeGenerationRequest(repositoryType: RepositoryType): SupportedCodeGenerationRepositoryType | undefined {
        switch (repositoryType) {
            case RepositoryType.TEMPLATE:
                return RepositoryType.TEMPLATE;
            case RepositoryType.SOLUTION:
                return RepositoryType.SOLUTION;
            case RepositoryType.TESTS:
                return RepositoryType.TESTS;
            default:
                return undefined;
        }
    }

    /**
     * Creates the request payload used to start code generation or perform a slot/check-only probe.
     * @param repositoryType repository to generate
     * @param checkOnly whether the request should only query the current generation status
     * @returns a request object matching the backend's runtime contract
     */
    private createCodeGenerationRequest(repositoryType: RepositoryType, checkOnly = false): CodeGenerationRequest {
        // Runtime contract: backend expects RepositoryType enum names (e.g. TEMPLATE), while generated OpenAPI type currently exposes repository names (e.g. exercise).
        const request: CodeGenerationRequest & { selectedFeedbackThreadIds?: number[] } = { repositoryType, checkOnly } as unknown as CodeGenerationRequest & {
            selectedFeedbackThreadIds?: number[];
        };
        if (!checkOnly) {
            const selectedFeedbackThreadIds = this.exerciseReviewCommentService.getSelectedFeedbackThreadIdsForRepository(
                repositoryType,
                repositoryType === RepositoryType.AUXILIARY ? this.selectedRepositoryId : undefined,
            );
            if (selectedFeedbackThreadIds.length > 0) {
                request.selectedFeedbackThreadIds = selectedFeedbackThreadIds;
            }
        }
        return request;
    }

    /**
     * Creates a request that checks whether a generation job is active without starting a new one.
     * @returns check-only request payload for the Hyperion endpoint
     */
    private createCheckOnlyCodeGenerationRequest(): CodeGenerationRequest {
        return { checkOnly: true } as unknown as CodeGenerationRequest;
    }

    /**
     * Subscribes to job updates, refreshes files on updates, and stops spinner on terminal events.
     * @param jobId job identifier
     */
    private subscribeToJob(jobId: string, repositoryType: SupportedCodeGenerationRepositoryType) {
        if (this.activeJobId === jobId && this.jobSubscription) {
            return;
        }
        this.clearJobSubscription(false);
        this.activeJobId = jobId;

        this.isGeneratingCode.set(true);
        this.jobSubscription = this.hyperionWs.subscribeToJob(jobId).subscribe({
            next: (event) => this.handleCodeGenerationJobEvent(repositoryType, event),
            error: () => this.stopCodeGenerationQueue(repositoryType, undefined, 'artemisApp.programmingExercise.codeGeneration.error'),
            complete: () => {
                // don't auto-stop spinner here; DONE/ERROR/timeout handle it
            },
        });

        // Safety timeout (20 minutes)
        this.jobTimeoutHandle = window.setTimeout(() => {
            if (this.isGeneratingCode() && this.activeCodeGenerationRepository === repositoryType) {
                this.stopCodeGenerationQueue(
                    repositoryType,
                    this.translateService.instant('artemisApp.programmingExercise.codeGeneration.timeoutDetails'),
                    'artemisApp.programmingExercise.codeGeneration.timeout',
                );
            }
        }, 1_200_000);
    }

    /**
     * Toggles the repository-selection popover for multi-repository generation.
     * @param event click event from the settings trigger
     */
    toggleCodeGenerationSettings(event: Event): void {
        this.codeGenerationStatusPopover()?.hide();
        const popover = this.codeGenerationSettingsPopover();
        if (!popover) {
            return;
        }

        if (popover.overlayVisible) {
            popover.hide();
            return;
        }

        popover.show(event, event.currentTarget as HTMLElement | undefined);
    }

    /**
     * Toggles the live generation status popover.
     * @param event click event from the status trigger
     * @param target optional target element used for popover alignment
     */
    toggleCodeGenerationStatus(event: Event, target?: HTMLElement): void {
        this.codeGenerationSettingsPopover()?.hide();
        const popover = this.codeGenerationStatusPopover();
        if (!popover) {
            return;
        }

        if (popover.overlayVisible) {
            popover.hide();
            return;
        }

        popover.show(event, target || (event.currentTarget as HTMLElement | undefined));
        this.scheduleCodeGenerationStatusPopoverRealign();
    }

    /**
     * Returns whether the given repository is currently selected for code generation.
     * @param repositoryType repository to inspect
     * @returns true if generation is enabled for the repository
     */
    isCodeGenerationRepositoryEnabled(repositoryType: SupportedCodeGenerationRepositoryType): boolean {
        return this.codeGenerationStatuses().find((status) => status.repositoryType === repositoryType)?.enabled ?? false;
    }

    /**
     * Toggles the generation selection state for a repository.
     * @param repositoryType repository to enable or disable
     */
    toggleCodeGenerationRepository(repositoryType: SupportedCodeGenerationRepositoryType): void {
        this.setCodeGenerationRepositoryEnabled(repositoryType, !this.isCodeGenerationRepositoryEnabled(repositoryType));
    }

    /**
     * Enables or disables code generation for a repository.
     * @param repositoryType repository to update
     * @param enabled whether the repository should be included in the next run
     */
    setCodeGenerationRepositoryEnabled(repositoryType: SupportedCodeGenerationRepositoryType, enabled: boolean): void {
        if (this.isGeneratingCode()) {
            return;
        }

        this.hasCustomCodeGenerationSelection = true;
        this.updateCodeGenerationStatus(repositoryType, (status) => ({ ...status, enabled }));
    }

    /**
     * Returns the translation key for a repository label in the generation UI.
     * @param repositoryType repository to translate
     * @returns translation key for the repository label
     */
    getCodeGenerationRepositoryTranslationKey(repositoryType: SupportedCodeGenerationRepositoryType): CodeGenerationRepositoryTranslationKey {
        return `artemisApp.programmingExercise.codeGeneration.repositories.${repositoryType.toLowerCase() as Lowercase<SupportedCodeGenerationRepositoryType>}`;
    }

    /**
     * Returns the translation key for a repository generation state.
     * @param state repository generation state
     * @returns translation key for the state label
     */
    getCodeGenerationStateTranslationKey(state: CodeGenerationExecutionState): CodeGenerationStateTranslationKey {
        return `artemisApp.programmingExercise.codeGeneration.status.${state}`;
    }

    /**
     * Returns the CSS text color class for a repository generation state.
     * @param state repository generation state
     * @returns Bootstrap text color class used in the status popover
     */
    getCodeGenerationStateClass(state: CodeGenerationExecutionState): string {
        return CODE_GENERATION_STATE_CLASSES[state];
    }

    /**
     * Returns the translation key for a file activity event.
     * @param eventType file activity event type
     * @returns translation key describing the file activity
     */
    getCodeGenerationFileActionTranslationKey(eventType: CodeGenerationFileEventType): CodeGenerationFileActionTranslationKey {
        return CODE_GENERATION_FILE_ACTION_TRANSLATION_KEYS[eventType];
    }

    /**
     * Clears the active job subscription and optionally stops the generation spinner.
     * @param stopSpinner whether the global generation indicator should be switched off
     */
    private clearJobSubscription(stopSpinner: boolean) {
        if (stopSpinner) {
            this.clearCodeGenerationRepositoryPulls();
            this.isGeneratingCode.set(false);
        }
        if (this.activeJobId) {
            this.hyperionWs.unsubscribeFromJob(this.activeJobId);
            this.activeJobId = undefined;
        }
        this.jobSubscription?.unsubscribe();
        this.jobSubscription = undefined;
        if (this.jobTimeoutHandle) {
            clearTimeout(this.jobTimeoutHandle);
            this.jobTimeoutHandle = undefined;
        }
    }

    /**
     * Processes job events for the active repository generation run.
     * @param repositoryType repository currently associated with the job
     * @param event websocket event emitted by Hyperion
     */
    private handleCodeGenerationJobEvent(repositoryType: SupportedCodeGenerationRepositoryType, event: HyperionEvent) {
        if (event.type === 'FILE_UPDATED' || event.type === 'NEW_FILE') {
            this.registerCodeGenerationFileActivity(repositoryType, event.type, event.path, event.iteration);
            this.scheduleCodeGenerationRepositoryPull(repositoryType);
            return;
        }

        if (event.type === 'DONE') {
            const completionState = this.getCodeGenerationExecutionState(event);
            this.flushCodeGenerationRepositoryPull(repositoryType);
            this.codeEditorContainer?.actions?.executeRefresh();
            this.updateCodeGenerationStatus(repositoryType, (status) => ({
                ...status,
                state: completionState,
                attempts: event.attempts,
                message: event.message,
            }));
            this.showCodeGenerationCompletionAlert(repositoryType, completionState);
            this.finishCurrentCodeGeneration(true);
            return;
        }

        if (event.type === 'ERROR') {
            this.stopCodeGenerationQueue(repositoryType, event.message, 'artemisApp.programmingExercise.codeGeneration.error');
        }
    }

    /**
     * Finalizes the active repository run and optionally advances the queue.
     * @param continueQueue whether queued repositories should still be processed
     */
    private finishCurrentCodeGeneration(continueQueue: boolean) {
        const hasMoreRepositories = continueQueue && this.queuedCodeGenerationRepositories.length > 0;
        this.clearJobSubscription(false);
        this.activeCodeGenerationRepository = undefined;

        if (hasMoreRepositories) {
            this.waitForCodeGenerationSlotRelease();
        } else {
            this.isGeneratingCode.set(false);
        }
    }

    /**
     * Stops the current generation queue, marks remaining repositories as skipped, and optionally shows an alert.
     * @param repositoryType repository whose run failed or timed out
     * @param message optional detailed message shown in the status card
     * @param alertTranslationKey translation key for the alert to display
     * @param showAlert whether an alert should be shown to the user
     */
    private stopCodeGenerationQueue(
        repositoryType: SupportedCodeGenerationRepositoryType,
        message: string | undefined,
        alertTranslationKey: 'artemisApp.programmingExercise.codeGeneration.error' | 'artemisApp.programmingExercise.codeGeneration.timeout',
        showAlert = true,
    ) {
        this.updateCodeGenerationStatus(repositoryType, (status) => ({
            ...status,
            state: 'error',
            message,
        }));
        this.markQueuedCodeGenerationRepositoriesSkipped(repositoryType);
        this.queuedCodeGenerationRepositories = [];
        this.activeCodeGenerationRepository = undefined;
        this.clearCodeGenerationStatusSubscription();
        this.clearSlotReleasePoll();
        this.clearJobSubscription(true);
        if (showAlert) {
            this.codeGenAlertService.addAlert({
                type: alertTranslationKey === 'artemisApp.programmingExercise.codeGeneration.timeout' ? AlertType.WARNING : AlertType.DANGER,
                translationKey: alertTranslationKey,
                translationParams: { repositoryType },
            });
        }
    }

    /**
     * Marks any repositories still waiting in the queue as skipped.
     */
    private markQueuedCodeGenerationRepositoriesSkipped(repositoryTypeToKeep?: SupportedCodeGenerationRepositoryType) {
        if (!this.queuedCodeGenerationRepositories.length) {
            return;
        }

        const skippedMessage = this.translateService.instant('artemisApp.programmingExercise.codeGeneration.status.skippedMessage');
        const queuedRepositories = new Set(this.queuedCodeGenerationRepositories.filter((repositoryType) => repositoryType !== repositoryTypeToKeep));
        if (!queuedRepositories.size) {
            return;
        }
        this.codeGenerationStatuses.update((statuses) =>
            statuses.map((status) =>
                queuedRepositories.has(status.repositoryType)
                    ? {
                          ...status,
                          state: 'skipped',
                          message: skippedMessage,
                      }
                    : status,
            ),
        );
    }

    /**
     * Records file activity emitted for a repository during generation.
     * @param repositoryType repository where the file change occurred
     * @param eventType file activity event type
     * @param path changed file path
     */
    private registerCodeGenerationFileActivity(repositoryType: SupportedCodeGenerationRepositoryType, eventType: CodeGenerationFileEventType, path: string, iteration?: number) {
        const activity: CodeGenerationFileActivity = {
            repositoryType,
            eventType,
            path,
            iteration,
            timestamp: Date.now(),
        };
        this.updateCodeGenerationStatus(repositoryType, (status) => ({
            ...status,
            fileActivities: [activity, ...status.fileActivities],
        }));
    }

    getCodeGenerationIterationActivityGroups(fileActivities: CodeGenerationFileActivity[]): CodeGenerationIterationActivityGroup[] {
        const groups = new Map<number | 'unknown', CodeGenerationFileActivity[]>();
        for (const activity of fileActivities) {
            const key = activity.iteration ?? 'unknown';
            const existingActivities = groups.get(key) ?? [];
            existingActivities.push(activity);
            groups.set(key, existingActivities);
        }

        return Array.from(groups.entries())
            .sort(([leftIteration], [rightIteration]) => {
                if (leftIteration === 'unknown') {
                    return 1;
                }
                if (rightIteration === 'unknown') {
                    return -1;
                }
                return rightIteration - leftIteration;
            })
            .map(([iteration, activities]) => ({
                iteration: iteration === 'unknown' ? undefined : iteration,
                activities,
            }));
    }

    /**
     * Toggles the selected-feedback details for a repository in the generation settings popover.
     * @param repositoryType repository whose selected threads should be shown or hidden
     */
    toggleFeedbackSummaryRepository(repositoryType: SupportedCodeGenerationRepositoryType): void {
        this.expandedFeedbackSummaryRepositories.update((expandedRepositories) =>
            expandedRepositories.includes(repositoryType)
                ? expandedRepositories.filter((expandedRepositoryType) => expandedRepositoryType !== repositoryType)
                : [...expandedRepositories, repositoryType],
        );
    }

    /**
     * Returns whether the selected-feedback details for a repository are currently expanded.
     * @param repositoryType repository to inspect
     * @returns true if the repository summary is expanded
     */
    isFeedbackSummaryRepositoryExpanded(repositoryType: SupportedCodeGenerationRepositoryType): boolean {
        return this.expandedFeedbackSummaryRepositories().includes(repositoryType);
    }

    /**
     * Navigates from the generation settings popover to a selected feedback thread.
     * @param thread selected feedback thread summary entry
     */
    navigateToSelectedFeedbackThread(thread: CodeGenerationSelectedFeedbackThread): void {
        this.codeGenerationSettingsPopover()?.hide();
        this.onNavigateToReviewCommentLocation({
            threadId: thread.threadId,
            targetType: thread.targetType,
            filePath: thread.filePath,
            lineNumber: thread.lineNumber,
            auxiliaryRepositoryId: thread.auxiliaryRepositoryId,
        });
    }

    private getCodeGenerationExecutionState(event: Extract<HyperionEvent, { type: 'DONE' }>): Extract<CodeGenerationExecutionState, 'success' | 'warning' | 'error'> {
        const completionStatus: HyperionCompletionStatus = event.completionStatus ?? (event.success ? 'SUCCESS' : 'ERROR');
        switch (completionStatus) {
            case 'SUCCESS':
                return 'success';
            case 'PARTIAL':
                return 'warning';
            default:
                return 'error';
        }
    }

    private showCodeGenerationCompletionAlert(
        repositoryType: SupportedCodeGenerationRepositoryType,
        completionState: Extract<CodeGenerationExecutionState, 'success' | 'warning' | 'error'>,
    ) {
        const alertByState: Record<typeof completionState, { type: AlertType; translationKey: string }> = {
            success: {
                type: AlertType.SUCCESS,
                translationKey: 'artemisApp.programmingExercise.codeGeneration.success',
            },
            warning: {
                type: AlertType.WARNING,
                translationKey: 'artemisApp.programmingExercise.codeGeneration.warning',
            },
            error: {
                type: AlertType.DANGER,
                translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
            },
        };
        const alert = alertByState[completionState];
        this.codeGenAlertService.addAlert({
            type: alert.type,
            translationKey: alert.translationKey,
            translationParams: { repositoryType },
        });
    }

    /**
     * Debounces repository refreshes triggered by file activity for the currently selected repository.
     * @param repositoryType repository whose working tree should be refreshed
     */
    private scheduleCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType) {
        if (this.selectedRepository !== repositoryType) {
            return;
        }

        this.repositoriesWithPendingCodeGenerationPull.add(repositoryType);
        const existingTimeoutHandle = this.codeGenerationPullTimeoutHandles.get(repositoryType);
        if (existingTimeoutHandle) {
            clearTimeout(existingTimeoutHandle);
        }

        const timeoutHandle = window.setTimeout(() => {
            this.codeGenerationPullTimeoutHandles.delete(repositoryType);
            this.flushCodeGenerationRepositoryPull(repositoryType);
        }, CODE_GENERATION_FILE_PULL_DEBOUNCE_MS);
        this.codeGenerationPullTimeoutHandles.set(repositoryType, timeoutHandle);
    }

    /**
     * Pulls repository changes immediately when a debounced refresh becomes due.
     * @param repositoryType repository to refresh
     */
    private flushCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType) {
        this.clearScheduledCodeGenerationRepositoryPull(repositoryType);
        if (!this.canStartCodeGenerationRepositoryPull(repositoryType)) {
            return;
        }

        this.startCodeGenerationRepositoryPull(repositoryType);
    }

    private clearScheduledCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType) {
        const existingTimeoutHandle = this.codeGenerationPullTimeoutHandles.get(repositoryType);
        if (existingTimeoutHandle) {
            clearTimeout(existingTimeoutHandle);
            this.codeGenerationPullTimeoutHandles.delete(repositoryType);
        }
    }

    private canStartCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType): boolean {
        if (!this.repositoriesWithPendingCodeGenerationPull.has(repositoryType)) {
            return false;
        }

        if (this.selectedRepository !== repositoryType) {
            this.repositoriesWithPendingCodeGenerationPull.delete(repositoryType);
            return false;
        }

        return !this.repositoriesWithInFlightCodeGenerationPull.has(repositoryType);
    }

    private startCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType) {
        this.repositoriesWithPendingCodeGenerationPull.delete(repositoryType);
        this.repositoriesWithInFlightCodeGenerationPull.add(repositoryType);
        this.codeGenerationPullSubscriptions.get(repositoryType)?.unsubscribe();
        const pullSubscription = this.repoService
            .pull()
            .pipe(
                take(1),
                catchError(() => {
                    return of(void 0);
                }),
            )
            .subscribe({
                complete: () => this.handleCompletedCodeGenerationRepositoryPull(repositoryType),
            });
        this.codeGenerationPullSubscriptions.set(repositoryType, pullSubscription);
    }

    private handleCompletedCodeGenerationRepositoryPull(repositoryType: SupportedCodeGenerationRepositoryType) {
        this.codeGenerationPullSubscriptions.delete(repositoryType);
        this.repositoriesWithInFlightCodeGenerationPull.delete(repositoryType);
        if (this.repositoriesWithPendingCodeGenerationPull.has(repositoryType)) {
            this.flushCodeGenerationRepositoryPull(repositoryType);
        }
    }

    /**
     * Clears all pending repository-refresh timers and bookkeeping for generation file activity.
     */
    private clearCodeGenerationRepositoryPulls() {
        this.codeGenerationPullTimeoutHandles.forEach((timeoutHandle) => clearTimeout(timeoutHandle));
        this.codeGenerationPullTimeoutHandles.clear();
        this.codeGenerationPullSubscriptions.forEach((subscription) => subscription.unsubscribe());
        this.codeGenerationPullSubscriptions.clear();
        this.repositoriesWithPendingCodeGenerationPull.clear();
        this.repositoriesWithInFlightCodeGenerationPull.clear();
    }

    /**
     * Returns the repositories currently selected for generation in the configured execution order.
     * @returns selected repositories in generation order
     */
    private getSelectedCodeGenerationRepositories(): SupportedCodeGenerationRepositoryType[] {
        const enabledRepositories = new Set(
            this.codeGenerationStatuses()
                .filter((status) => status.enabled)
                .map((status) => status.repositoryType),
        );

        return SUPPORTED_CODE_GENERATION_REPOSITORIES.filter((repositoryType) => enabledRepositories.has(repositoryType));
    }

    /**
     * Resets the per-repository status cards for a new generation run.
     * @param repositories repositories participating in the new run
     */
    private initializeCodeGenerationRunStatuses(repositories: SupportedCodeGenerationRepositoryType[]) {
        const enabledRepositories = new Set(repositories);
        this.codeGenerationStatuses.set(
            SUPPORTED_CODE_GENERATION_REPOSITORIES.map((repositoryType) => ({
                repositoryType,
                enabled: enabledRepositories.has(repositoryType),
                state: enabledRepositories.has(repositoryType) ? 'queued' : 'idle',
                attempts: undefined,
                message: undefined,
                fileActivities: [],
            })),
        );
        this.scheduleCodeGenerationStatusPopoverRealign();
    }

    /**
     * Syncs the default repository selection to the repository currently open in the editor.
     */
    private syncCodeGenerationSelectionWithSelectedRepository() {
        const repositoryType = this.mapRepositoryTypeToCodeGenerationRequest(this.selectedRepository);
        this.codeGenerationStatuses.update((statuses) =>
            statuses.map((status) => ({
                ...status,
                enabled: repositoryType === status.repositoryType,
            })),
        );
    }

    /**
     * Creates the initial status object for a repository card.
     * @param repositoryType repository represented by the card
     * @returns default repository generation status
     */
    private createCodeGenerationStatus(repositoryType: SupportedCodeGenerationRepositoryType): CodeGenerationRepositoryStatus {
        return {
            repositoryType,
            enabled: false,
            state: 'idle',
            fileActivities: [],
        };
    }

    /**
     * Applies a status update to a repository card and realigns the status popover when visible.
     * @param repositoryType repository whose card should be updated
     * @param updater pure updater function for the repository status
     */
    private updateCodeGenerationStatus(repositoryType: SupportedCodeGenerationRepositoryType, updater: (status: CodeGenerationRepositoryStatus) => CodeGenerationRepositoryStatus) {
        this.codeGenerationStatuses.update((statuses) => statuses.map((status) => (status.repositoryType === repositoryType ? updater(status) : status)));
        this.scheduleCodeGenerationStatusPopoverRealign();
    }

    /**
     * Schedules a popover realignment after status content changes.
     */
    private scheduleCodeGenerationStatusPopoverRealign() {
        const popover = this.codeGenerationStatusPopover();
        if (!popover?.overlayVisible) {
            return;
        }

        window.setTimeout(() => {
            this.realignCodeGenerationStatusPopover();
        });
    }

    /**
     * Repositions the status popover so its pointer stays visually centered on the status button.
     */
    private realignCodeGenerationStatusPopover() {
        const popover = this.codeGenerationStatusPopover();
        const target = popover?.target as HTMLElement | undefined;
        const container = popover?.container;
        if (!popover?.overlayVisible || !target || !container) {
            return;
        }

        popover.align();

        const containerRect = container.getBoundingClientRect();
        const targetRect = target.getBoundingClientRect();
        const arrowTargetRect = this.getCodeGenerationStatusArrowTargetRect(target);
        const scrollLeft = window.scrollX;
        const viewportWidth = window.innerWidth;
        const borderRadius = parseFloat(window.getComputedStyle(container).getPropertyValue('border-radius')) || 0;
        const centeredLeft = targetRect.left + scrollLeft + targetRect.width / 2 - containerRect.width / 2 + CODE_GENERATION_STATUS_POPOVER_CENTER_OFFSET_PX;
        const clampedLeft = Math.max(scrollLeft, Math.min(centeredLeft, scrollLeft + viewportWidth - containerRect.width));
        container.style.insetInlineStart = `${clampedLeft}px`;

        const arrowLeft = Math.max(0, arrowTargetRect.left + scrollLeft + arrowTargetRect.width / 2 - clampedLeft - borderRadius * 3 - 2);
        container.style.setProperty('--p-popover-arrow-left', `${arrowLeft}px`);
    }

    /**
     * Returns the visual rect used as the arrow target for the status popover.
     * @param target popover target element
     * @returns icon rect when present, otherwise the full target rect
     */
    private getCodeGenerationStatusArrowTargetRect(target: HTMLElement): DOMRect {
        const iconElement = target.querySelector('svg');
        return iconElement?.getBoundingClientRect() ?? target.getBoundingClientRect();
    }

    /**
     * Polls the backend until the previous exercise-level generation slot has been released.
     * @param attempt current poll attempt number, starting at 1
     */
    private waitForCodeGenerationSlotRelease(attempt = 1) {
        if (!this.exercise?.id) {
            this.clearJobSubscription(true);
            return;
        }

        this.clearCodeGenerationStatusSubscription();
        this.statusSubscription = this.hyperionCodeGenerationApi.generateCode(this.exercise.id, this.createCheckOnlyCodeGenerationRequest()).subscribe({
            next: (res) => {
                if (!res?.jobId) {
                    this.clearCodeGenerationStatusSubscription();
                    this.clearSlotReleasePoll();
                    this.runNextCodeGeneration();
                    return;
                }

                this.scheduleNextSlotReleasePoll(attempt);
            },
            error: () => {
                this.scheduleNextSlotReleasePoll(attempt);
            },
        });
    }

    /**
     * Schedules the next slot-release poll attempt or fails the queue after the retry limit.
     * @param attempt current poll attempt number, starting at 1
     */
    private scheduleNextSlotReleasePoll(attempt: number) {
        if (attempt >= CODE_GENERATION_SLOT_RELEASE_MAX_POLLS) {
            const nextRepository = this.queuedCodeGenerationRepositories[0];
            if (nextRepository) {
                this.stopCodeGenerationQueue(
                    nextRepository,
                    this.translateService.instant('artemisApp.programmingExercise.codeGeneration.queueReleaseTimeoutDetails'),
                    'artemisApp.programmingExercise.codeGeneration.error',
                );
            } else {
                this.clearJobSubscription(true);
            }
            return;
        }

        this.clearSlotReleasePoll();
        this.slotReleasePollTimeoutHandle = window.setTimeout(() => {
            this.waitForCodeGenerationSlotRelease(attempt + 1);
        }, CODE_GENERATION_SLOT_RELEASE_POLL_INTERVAL_MS);
    }

    /**
     * Clears the active HTTP subscription used for restore checks or slot-release polling.
     */
    private clearCodeGenerationStatusSubscription() {
        this.statusSubscription?.unsubscribe();
        this.statusSubscription = undefined;
    }

    /**
     * Clears the scheduled timer used for slot-release polling retries.
     */
    private clearSlotReleasePoll() {
        if (this.slotReleasePollTimeoutHandle) {
            clearTimeout(this.slotReleasePollTimeoutHandle);
            this.slotReleasePollTimeoutHandle = undefined;
        }
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
        this.selectedIssue = undefined;
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

        this.consistencyCheckService.checkConsistencyForProgrammingExercise(exercise.id!).subscribe({
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
     * @param {ConsistencyIssue.SeverityEnum} severity
     *        The severity that determines the returned icon.
     *
     * @returns
     *          A FontAwesome icon representing high, medium, or low severity.
     */
    getSeverityIcon(severity: ConsistencyIssue.SeverityEnum | undefined) {
        switch (severity) {
            case ConsistencyIssue.SeverityEnum.High:
                return this.faCircleExclamation;
            case ConsistencyIssue.SeverityEnum.Medium:
                return this.faTriangleExclamation;
            case ConsistencyIssue.SeverityEnum.Low:
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
        this.refinementPopover()?.toggle(event, target);
    }

    /**
     * Submits the full problem statement refinement.
     * Hides the popover, then delegates to the shared AI operations helper.
     */
    submitRefinement(): void {
        const prompt = this.refinementPrompt().trim();
        if (!prompt || !this.exercise) return;

        this.refinementPopover()?.hide();
        this.aiOps.handleProblemStatementAction(this.exercise, this.editableInstructions());
    }

    /**
     * Handles inline refinement request from editor selection.
     */
    onInlineRefinement(event: InlineRefinementEvent): void {
        this.aiOps.onInlineRefinement(this.exercise, this.editableInstructions(), event);
    }

    /**
     * Returns a Bootstrap text color class based on an issue's severity.
     *
     * @param {ConsistencyIssue.SeverityEnum} severity
     *        The severity that determines the color.
     *
     * @returns
     *          A text color class (`text-danger`, `text-warning`, `text-info`, or `text-secondary`).
     */
    getSeverityColor(severity: ConsistencyIssue.SeverityEnum | undefined) {
        switch (severity) {
            case ConsistencyIssue.SeverityEnum.High:
                return 'text-danger';
            case ConsistencyIssue.SeverityEnum.Medium:
                return 'text-warning';
            case ConsistencyIssue.SeverityEnum.Low:
                return 'text-info';
            default:
                return 'text-secondary';
        }
    }

    readonly totalLocationsCount = computed(() => this.sortedIssues().length);
    readonly showConsistencyIssuesToolbar = signal(false);

    get currentGlobalIndex(): number {
        const issues = this.sortedIssues();
        if (!this.selectedIssue) {
            return 0;
        }
        const index = issues.findIndex((issue) => issue.threadId === this.selectedIssue?.threadId);
        return index >= 0 ? index + 1 : 0;
    }

    toggleConsistencyIssuesToolbar() {
        this.showConsistencyIssuesToolbar.update((v) => !v);
        const issues = this.sortedIssues();

        if (this.showConsistencyIssuesToolbar()) {
            const isIssueValid = this.selectedIssue && issues.some((issue) => issue.threadId === this.selectedIssue?.threadId);
            if (!isIssueValid && issues.length > 0) {
                this.selectedIssue = issues[0];
                this.jumpToLocation(this.selectedIssue);
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
        if (this.selectedIssue) {
            currentIndex = issues.findIndex((issue) => issue.threadId === this.selectedIssue?.threadId);
        }

        let newIndex = currentIndex + step;
        if (newIndex >= issues.length) {
            newIndex = 0;
        } else if (newIndex < 0) {
            newIndex = issues.length - 1;
        }

        this.selectedIssue = issues[newIndex];
        this.jumpToLocation(this.selectedIssue);
    }

    /**
     * Navigates to a review-thread location emitted by review comment widgets.
     */
    onNavigateToReviewCommentLocation(location: ReviewThreadLocation): void {
        if (location.threadId !== undefined) {
            const selectedIssue = this.sortedIssues().find((issue) => issue.threadId === location.threadId);
            if (selectedIssue) {
                this.selectedIssue = selectedIssue;
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

    private mapThreadToSelectedFeedbackThread(thread: CommentThread): CodeGenerationSelectedFeedbackThread {
        const filePath = thread.filePath ?? thread.initialFilePath ?? undefined;
        const lineNumber = thread.lineNumber ?? thread.initialLineNumber;

        return {
            threadId: thread.id,
            targetType: thread.targetType,
            auxiliaryRepositoryId: thread.auxiliaryRepositoryId,
            filePath,
            lineNumber,
            locationLabel: this.getSelectedFeedbackThreadLocationLabel(filePath, lineNumber),
        };
    }

    private getSelectedFeedbackThreadLocationLabel(filePath?: string, lineNumber?: number): string {
        if (filePath && lineNumber !== undefined && lineNumber > 0) {
            return `${filePath}:${lineNumber}`;
        }
        if (filePath) {
            return filePath;
        }
        if (lineNumber !== undefined && lineNumber > 0) {
            return this.translateService.instant('artemisApp.programmingExercise.codeGeneration.selectedFeedback.line', { line: lineNumber });
        }
        return this.translateService.instant('artemisApp.programmingExercise.codeGeneration.selectedFeedback.unknownLocation');
    }

    private navigateToLocation(location: { targetType: CommentThreadLocationType; filePath?: string; lineNumber?: number; auxiliaryRepositoryId?: number }): void {
        if (location.targetType === CommentThreadLocationType.PROBLEM_STATEMENT) {
            this.codeEditorContainer.selectedFile = this.codeEditorContainer.problemStatementIdentifier;
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

        try {
            switch (location.targetType) {
                case CommentThreadLocationType.TEMPLATE_REPO:
                    if (this.codeEditorContainer.selectedRepository() !== RepositoryType.TEMPLATE) {
                        this.selectTemplateParticipation();
                        return;
                    }
                    break;
                case CommentThreadLocationType.SOLUTION_REPO:
                    if (this.codeEditorContainer.selectedRepository() !== RepositoryType.SOLUTION) {
                        this.selectSolutionParticipation();
                        return;
                    }
                    break;
                case CommentThreadLocationType.TEST_REPO:
                    if (this.codeEditorContainer.selectedRepository() !== RepositoryType.TESTS) {
                        this.selectTestRepository();
                        return;
                    }
                    break;
                case CommentThreadLocationType.AUXILIARY_REPO: {
                    const auxiliaryRepositoryId = location.auxiliaryRepositoryId;
                    if (
                        auxiliaryRepositoryId !== undefined &&
                        (this.codeEditorContainer.selectedRepository() !== RepositoryType.AUXILIARY || this.selectedRepositoryId !== auxiliaryRepositoryId)
                    ) {
                        this.selectAuxiliaryRepository(auxiliaryRepositoryId);
                        return;
                    }
                    break;
                }
                default:
            }
        } catch {
            this.alertService.error('artemisApp.hyperion.consistencyCheck.navigationFailed');
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
        if (this.fileToJumpOn) {
            // File already loaded: avoid re-running the file-load/sync path, which is only
            // needed when Monaco actually switches files. We only need the deferred line jump.
            if (this.codeEditorContainer.selectedFile === this.fileToJumpOn) {
                if (this.lineJumpOnFileLoad !== undefined) {
                    this.codeEditorContainer.jumpToLine(this.lineJumpOnFileLoad);
                }
                this.lineJumpOnFileLoad = undefined;
                this.fileToJumpOn = undefined;
            // File already loaded, no file-load event will fire.
            // Jump directly without re-running file-sync load/rebind.
            if (this.codeEditorContainer.selectedFile === this.fileToJumpOn) {
                this.performDeferredLineJump(this.fileToJumpOn);
                return;
            }

            // Will load file and signal to fileLoad when finished loading
            this.codeEditorContainer.selectedFile = this.fileToJumpOn;
        }
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
                this.codeEditorContainer.jumpToLine(this.lineJumpOnFileLoad);
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
