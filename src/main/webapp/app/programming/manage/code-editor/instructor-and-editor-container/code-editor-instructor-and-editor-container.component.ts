import { Component, DestroyRef, Injector, OnDestroy, TemplateRef, ViewChild, computed, inject, signal, viewChild } from '@angular/core';
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
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from '../../status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CodeGenerationRequestDTO } from 'app/openapi/model/codeGenerationRequestDTO';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { Observable, Subscription, catchError, of, take, tap } from 'rxjs';
import { ProblemStatementAiOperationsHelper } from 'app/programming/manage/shared/problem-statement-ai-operations.helper';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { CommentThreadLocationType } from 'app/exercise/shared/entities/review/comment-thread.model';

import { getRepoPath } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { ProblemStatementService } from 'app/programming/manage/services/problem-statement.service';
import { InlineRefinementEvent, MAX_USER_PROMPT_LENGTH } from 'app/programming/manage/shared/problem-statement.utils';
import { TooltipModule } from 'primeng/tooltip';
import { TextareaModule } from 'primeng/textarea';
import { BadgeModule } from 'primeng/badge';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { Popover, PopoverModule } from 'primeng/popover';

const SEVERITY_ORDER = {
    HIGH: 0,
    MEDIUM: 1,
    LOW: 2,
} as const;

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
    readonly consistencyIssues = signal<ConsistencyIssue[]>([]);
    readonly sortedIssues = computed(() => [...this.consistencyIssues()].sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity]));

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
    /** Prompt bound to the refinement popover textarea — aliased to aiOps.userPrompt. */
    readonly refinementPrompt = this.aiOps.userPrompt;
    protected readonly faPaperPlane = faPaperPlane;

    private consistencyCheckService = inject(ConsistencyCheckService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private exerciseReviewCommentService = inject(ExerciseReviewCommentService);

    lineJumpOnFileLoad: number | undefined = undefined;
    fileToJumpOn: string | undefined = undefined;
    selectedIssue: ConsistencyIssue | undefined = undefined;
    locationIndex: number = 0;

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

    constructor() {
        super();
        this.aiOps.setChangeHandler({
            onContentChanged: (content) => {
                this.onInstructionChanged(content);
            },
        });
    }

    override loadExercise(exerciseId: number): Observable<ProgrammingExercise> {
        return super.loadExercise(exerciseId).pipe(
            tap((exercise) => {
                if (exercise.id) {
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

        if (this.selectedRepository !== RepositoryType.TEMPLATE && this.selectedRepository !== RepositoryType.SOLUTION && this.selectedRepository !== RepositoryType.TESTS) {
            this.codeGenAlertService.addAlert({ type: AlertType.WARNING, translationKey: 'artemisApp.programmingExercise.codeGeneration.unsupportedRepository' });
            return;
        }
        const modalRef = this.modalService.open(ConfirmAutofocusModalComponent, { keyboard: true, size: 'md' });
        modalRef.componentInstance.title = 'artemisApp.programmingExercise.codeGeneration.confirmTitle';
        modalRef.componentInstance.text = 'artemisApp.programmingExercise.codeGeneration.confirmText';
        modalRef.componentInstance.translateText = true;
        modalRef.result.then(() => this.startCodeGeneration()).catch(() => {});
    }

    /**
     * Triggers the async generation endpoint and subscribes to job updates.
     */
    private startCodeGeneration() {
        this.isGeneratingCode.set(true);
        const repositoryType = this.selectedRepository as CodeGenerationRequestDTO.RepositoryTypeEnum;
        const exerciseId = this.exercise!.id!;
        this.hyperionCodeGenerationApi.generateCode(exerciseId, { repositoryType }).subscribe({
            next: (res) => {
                if (!res?.jobId) {
                    this.isGeneratingCode.set(false);
                    this.codeGenAlertService.addAlert({
                        type: AlertType.DANGER,
                        translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                    });
                    return;
                }
                this.subscribeToJob(res.jobId);
            },
            error: (error: HttpErrorResponse) => {
                this.isGeneratingCode.set(false);
                if (this.isCodeGenerationAlreadyRunning(error)) {
                    this.openCodeGenerationRunningModal();
                    return;
                }
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                });
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

    private openCodeGenerationRunningModal(): void {
        this.modalService.open(this.codeGenerationRunningModal, { backdrop: 'static', keyboard: false, size: 'md' });
    }

    protected override applyDomainChange(domainType: any, domainValue: any) {
        super.applyDomainChange(domainType, domainValue);
        this.restoreCodeGenerationState();
    }

    override ngOnDestroy() {
        this.clearJobSubscription(true);
        this.statusSubscription?.unsubscribe();
        this.aiOps.destroy();
        super.ngOnDestroy();
    }

    private restoreCodeGenerationState() {
        this.restoreRequestId += 1;
        this.statusSubscription?.unsubscribe();
        this.statusSubscription = undefined;

        if (!this.hyperionEnabled || !this.exercise?.id) {
            return;
        }
        if (this.isGeneratingCode()) {
            return;
        }
        if (this.selectedRepository !== RepositoryType.TEMPLATE && this.selectedRepository !== RepositoryType.SOLUTION && this.selectedRepository !== RepositoryType.TESTS) {
            return;
        }
        const repositoryType = this.selectedRepository as CodeGenerationRequestDTO.RepositoryTypeEnum;
        const requestId = this.restoreRequestId;
        this.statusSubscription = this.hyperionCodeGenerationApi.generateCode(this.exercise.id, { repositoryType, checkOnly: true }).subscribe({
            next: (res) => {
                if (requestId !== this.restoreRequestId) {
                    return;
                }
                if (res?.jobId) {
                    this.subscribeToJob(res.jobId);
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
     * Subscribes to job updates, refreshes files on updates, and stops spinner on terminal events.
     * @param jobId job identifier
     */
    private subscribeToJob(jobId: string) {
        if (this.activeJobId === jobId && this.jobSubscription) {
            return;
        }
        this.clearJobSubscription(false);
        this.activeJobId = jobId;
        const cleanup = () => {
            this.clearJobSubscription(true);
        };

        this.isGeneratingCode.set(true);
        this.jobSubscription = this.hyperionWs.subscribeToJob(jobId).subscribe({
            next: (event) => {
                switch (event.type) {
                    case 'STARTED':
                        // spinner already on; just log
                        break;

                    case 'PROGRESS':
                        break;

                    case 'FILE_UPDATED':
                    case 'NEW_FILE':
                        this.repoService
                            .pull()
                            .pipe(
                                take(1),
                                catchError(() => {
                                    return of(void 0);
                                }),
                            )
                            .subscribe(() => {});
                        break;

                    case 'DONE':
                        this.codeEditorContainer?.actions?.executeRefresh();
                        cleanup();
                        this.codeGenAlertService.addAlert({
                            type: event.success ? AlertType.SUCCESS : AlertType.WARNING,
                            translationKey: event.success
                                ? 'artemisApp.programmingExercise.codeGeneration.success'
                                : 'artemisApp.programmingExercise.codeGeneration.partialSuccess',
                            translationParams: { repositoryType: this.selectedRepository },
                        });
                        break;

                    case 'ERROR':
                        cleanup();
                        this.codeGenAlertService.addAlert({
                            type: AlertType.DANGER,
                            translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                            translationParams: { repositoryType: this.selectedRepository },
                        });
                        break;

                    default:
                }
            },
            error: () => {
                cleanup();
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                    translationParams: { repositoryType: this.selectedRepository },
                });
            },
            complete: () => {
                // don't auto-stop spinner here; DONE/ERROR/timeout handle it
            },
        });

        // Safety timeout (20 minutes)
        this.jobTimeoutHandle = window.setTimeout(() => {
            if (this.isGeneratingCode()) {
                cleanup();
                this.codeGenAlertService.addAlert({
                    type: AlertType.WARNING,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.timeout',
                });
            }
        }, 1_200_000);
    }

    private clearJobSubscription(stopSpinner: boolean) {
        if (stopSpinner) {
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
        // Clear previous consistency issues and reset toolbar state
        this.consistencyIssues.set([]);
        this.selectedIssue = undefined;
        this.showConsistencyIssuesToolbar.set(false);

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
                    next: (response: ConsistencyCheckResponse) => {
                        this.consistencyIssues.set(response.issues ?? []);

                        if (this.consistencyIssues().length === 0) {
                            this.alertService.success(this.translateService.instant('artemisApp.hyperion.consistencyCheck.noInconsistencies'));
                        } else {
                            this.alertService.warning(this.translateService.instant('artemisApp.hyperion.consistencyCheck.inconsistenciesFoundAlert'));
                            this.selectedIssue = this.consistencyIssues()[0];
                            this.showConsistencyIssuesToolbar.set(true);
                        }
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
    getSeverityIcon(severity: ConsistencyIssue.SeverityEnum) {
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
    getSeverityColor(severity: ConsistencyIssue.SeverityEnum) {
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

    readonly totalLocationsCount = computed(() => this.sortedIssues().reduce((acc, issue) => acc + (issue.relatedLocations?.length ?? 0), 0));
    readonly showConsistencyIssuesToolbar = signal(false);

    get currentGlobalIndex(): number {
        const issues = this.sortedIssues();
        let count = 0;
        for (const issue of issues) {
            if (issue === this.selectedIssue) {
                return count + this.locationIndex + 1; // 1-based
            }
            count += issue.relatedLocations?.length ?? 0;
        }
        return 0;
    }

    toggleConsistencyIssuesToolbar() {
        this.showConsistencyIssuesToolbar.update((v) => !v);
        const issues = this.sortedIssues();

        // If newly opened
        if (this.showConsistencyIssuesToolbar()) {
            // Check if selection is invalid (stale issue, issue not in list anymore, or index out of bounds)
            const isIssueValid = this.selectedIssue && issues.includes(this.selectedIssue);
            const isIndexValid =
                this.selectedIssue && this.selectedIssue.relatedLocations && this.locationIndex < this.selectedIssue.relatedLocations.length && this.locationIndex >= 0;

            if ((!isIssueValid || !isIndexValid) && issues.length > 0) {
                this.selectedIssue = issues[0];
                this.locationIndex = 0;
                // Jump to it immediately
                this.jumpToLocation(this.selectedIssue, 0);
            }
        }
    }

    /**
     * Navigates through consistency issues globally.
     * @param {number} step - Direction to navigate (1 for next, -1 for previous).
     */
    navigateGlobal(step: number): void {
        const issues = this.sortedIssues();
        if (!issues.length) return;

        // Flatten all locations
        const allLocations: { issue: ConsistencyIssue; locIndex: number }[] = [];
        issues.forEach((issue) => {
            (issue.relatedLocations || []).forEach((_, idx) => {
                allLocations.push({ issue, locIndex: idx });
            });
        });

        if (allLocations.length === 0) return;

        // Find current index
        let currentIndex = -1;
        if (this.selectedIssue) {
            currentIndex = allLocations.findIndex((item) => item.issue === this.selectedIssue && item.locIndex === this.locationIndex);
        }

        // Calculate new index
        let newIndex = currentIndex + step;
        if (newIndex >= allLocations.length) {
            newIndex = 0; // Wrap to start
        } else if (newIndex < 0) {
            newIndex = allLocations.length - 1; // Wrap to end
        }

        const target = allLocations[newIndex];
        this.selectedIssue = target.issue;
        this.locationIndex = target.locIndex;

        this.jumpToLocation(target.issue, target.locIndex);
    }

    /**
     * Helper to perform the actual editor jump.
     */
    private jumpToLocation(issue: ConsistencyIssue, index: number) {
        if (!issue.relatedLocations || !issue.relatedLocations[index]) {
            return;
        }
        const location = issue.relatedLocations[index];
        const targetType = (() => {
            switch (location.type) {
                case 'TEMPLATE_REPOSITORY':
                    return CommentThreadLocationType.TEMPLATE_REPO;
                case 'SOLUTION_REPOSITORY':
                    return CommentThreadLocationType.SOLUTION_REPO;
                case 'TESTS_REPOSITORY':
                    return CommentThreadLocationType.TEST_REPO;
                case 'PROBLEM_STATEMENT':
                default:
                    return CommentThreadLocationType.PROBLEM_STATEMENT;
            }
        })();
        this.navigateToLocation({
            targetType,
            filePath: targetType === CommentThreadLocationType.PROBLEM_STATEMENT ? undefined : getRepoPath(location),
            lineNumber: location.endLine,
        });
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
            // File already loaded, file load event will not fire
            if (this.codeEditorContainer.selectedFile === this.fileToJumpOn) {
                this.onFileLoad(this.fileToJumpOn!);
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
