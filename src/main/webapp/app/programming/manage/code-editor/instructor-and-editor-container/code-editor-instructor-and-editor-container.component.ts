import { Component, ViewChild, computed, inject, signal } from '@angular/core';
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
    faCheckDouble,
    faCircleExclamation,
    faCircleInfo,
    faCircleNotch,
    faPlus,
    faSpinner,
    faTimes,
    faTimesCircle,
    faTriangleExclamation,
} from '@fortawesome/free-solid-svg-icons';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseInstructorExerciseStatusComponent } from '../../status/programming-exercise-instructor-exercise-status.component';
import { NgbDropdown, NgbDropdownButtonItem, NgbDropdownItem, NgbDropdownMenu, NgbDropdownToggle, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CodeGenerationRequestDTO } from 'app/openapi/model/codeGenerationRequestDTO';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ConfirmAutofocusModalComponent } from 'app/shared/components/confirm-autofocus-modal/confirm-autofocus-modal.component';
import { HyperionWebsocketService } from 'app/hyperion/services/hyperion-websocket.service';
import { CodeEditorRepositoryService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { Observable, Subscription, catchError, map, of, take, tap } from 'rxjs';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';
import { ExerciseReviewCommentService } from 'app/exercise/services/exercise-review-comment.service';
import { CommentThread, CommentThreadLocationType, CreateCommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CreateComment } from 'app/exercise/shared/entities/review/comment.model';

const PROBLEM_STATEMENT_FILE_PATH = 'problem_statement.md';
import { getRepoPath } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check';
import { mapRepositoryToThreadLocationType } from 'app/programming/shared/code-editor/util/review-comment-utils';

const SEVERITY_ORDER = {
    HIGH: 0,
    MEDIUM: 1,
    LOW: 2,
} as const;

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-and-editor-container.component.html',
    styleUrl: 'code-editor-instructor-and-editor-container.scss',
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
        NgbDropdownButtonItem,
        NgbDropdownItem,
        NgbTooltip,
        UpdatingResultComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseEditableInstructionComponent,
        ProgrammingExerciseInstructionComponent,
    ],
})
export class CodeEditorInstructorAndEditorContainerComponent extends CodeEditorInstructorBaseContainerComponent {
    @ViewChild(UpdatingResultComponent, { static: false }) resultComp: UpdatingResultComponent;
    @ViewChild(ProgrammingExerciseEditableInstructionComponent, { static: false }) editableInstructions: ProgrammingExerciseEditableInstructionComponent;

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly MarkdownEditorHeight = MarkdownEditorHeight;
    readonly consistencyIssues = signal<ConsistencyIssue[]>([]);
    readonly reviewCommentThreads = signal<CommentThread[]>([]);
    readonly sortedIssues = computed(() => [...this.consistencyIssues()].sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity]));

    private consistencyCheckService = inject(ConsistencyCheckService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private profileService = inject(ProfileService);
    private exerciseReviewCommentService = inject(ExerciseReviewCommentService);

    lineJumpOnFileLoad: number | undefined = undefined;
    fileToJumpOn: string | undefined = undefined;
    selectedIssue: ConsistencyIssue | undefined = undefined;
    locationIndex: number = 0;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;
    faCircleExclamation = faCircleExclamation;
    faTriangleExclamation = faTriangleExclamation;
    faCircleInfo = faCircleInfo;

    faSpinner = faSpinner;
    facArtemisIntelligence = facArtemisIntelligence;

    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);

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

    override loadExercise(exerciseId: number): Observable<ProgrammingExercise> {
        return super.loadExercise(exerciseId).pipe(
            tap((exercise) => {
                if (exercise.id) {
                    this.loadReviewCommentThreads(exercise.id);
                }
            }),
        );
    }

    /**
     * Clears draft widgets and reloads review comment threads after a commit.
     */
    onCommit(): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }
        this.codeEditorContainer?.monacoEditor?.clearReviewCommentDrafts();
        this.editableInstructions?.clearReviewCommentDrafts();
        this.loadReviewCommentThreads(exerciseId);
    }

    onProblemStatementSaved(): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }
        this.editableInstructions?.clearReviewCommentDrafts();
        this.loadReviewCommentThreads(exerciseId);
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
            error: (err) => {
                this.isGeneratingCode.set(false);
                this.codeGenAlertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                });
            },
            complete: () => {},
        });
    }

    /**
     * Subscribes to job updates, refreshes files on updates, and stops spinner on terminal events.
     * @param jobId job identifier
     */
    private subscribeToJob(jobId: string) {
        const cleanup = () => {
            this.isGeneratingCode.set(false);
            this.hyperionWs.unsubscribeFromJob(jobId);
            this.jobSubscription?.unsubscribe();
            if (this.jobTimeoutHandle) {
                clearTimeout(this.jobTimeoutHandle);
                this.jobTimeoutHandle = undefined;
            }
        };

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
            error: (err) => {
                this.alertService.error(this.translateService.instant('artemisApp.hyperion.consistencyCheck.checkFailedAlert'));
            },
        });
    }

    /**
     * Creates a review comment thread for the current repository selection.
     *
     * @param event The line, file, and text of the new comment.
     */
    onSubmitReviewComment(event: { lineNumber: number; fileName: string; text: string }): void {
        const targetType = mapRepositoryToThreadLocationType(this.selectedRepository);
        const auxiliaryRepositoryId = this.selectedRepository === RepositoryType.AUXILIARY ? this.selectedRepositoryId : undefined;
        this.createThreadWithInitialComment(targetType, event.fileName, event.lineNumber, event.text, auxiliaryRepositoryId);
    }

    /**
     * Creates a review comment thread for the problem statement.
     *
     * @param event The line, file, and text of the new comment.
     */
    onSubmitProblemStatementReviewComment(event: { lineNumber: number; fileName: string; text: string }): void {
        this.createThreadWithInitialComment(CommentThreadLocationType.PROBLEM_STATEMENT, PROBLEM_STATEMENT_FILE_PATH, event.lineNumber, event.text);
    }

    /**
     * Deletes a review comment and updates the local thread state.
     *
     * @param commentId The id of the comment to delete.
     */
    onDeleteReviewComment(commentId: number): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }

        this.exerciseReviewCommentService
            .deleteComment(exerciseId, commentId)
            .pipe(
                tap(() => {
                    this.reviewCommentThreads.update((threads) => this.exerciseReviewCommentService.removeCommentFromThreads(threads, commentId));
                }),
                catchError(() => {
                    this.alertService.error('artemisApp.review.deleteFailed');
                    return of(null);
                }),
            )
            .subscribe();
    }

    /**
     * Creates a reply comment and updates the local thread state.
     *
     * @param event The thread id and reply text.
     */
    onReplyReviewComment(event: { threadId: number; text: string }): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }

        const createComment: CreateComment = { contentType: 'USER', text: event.text };

        this.exerciseReviewCommentService
            .createUserComment(exerciseId, event.threadId, createComment)
            .pipe(
                tap((response) => {
                    const createdComment = response.body;
                    if (!createdComment) {
                        return;
                    }
                    this.reviewCommentThreads.update((threads) => this.exerciseReviewCommentService.appendCommentToThreads(threads, createdComment));
                }),
                catchError(() => {
                    this.alertService.error('artemisApp.review.saveFailed');
                    return of(null);
                }),
            )
            .subscribe();
    }

    /**
     * Updates a comment's content and refreshes it in the local thread state.
     *
     * @param event The comment id and updated text.
     */
    onUpdateReviewComment(event: { commentId: number; text: string }): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }

        this.exerciseReviewCommentService
            .updateUserCommentContent(exerciseId, event.commentId, { contentType: 'USER', text: event.text })
            .pipe(
                tap((response) => {
                    const updatedComment = response.body;
                    if (!updatedComment) {
                        return;
                    }
                    this.reviewCommentThreads.update((threads) => this.exerciseReviewCommentService.updateCommentInThreads(threads, updatedComment));
                }),
                catchError(() => {
                    this.alertService.error('artemisApp.review.saveFailed');
                    return of(null);
                }),
            )
            .subscribe();
    }

    /**
     * Updates the resolved state of a thread and refreshes it locally.
     *
     * @param event The thread id and new resolved state.
     */
    onToggleResolveReviewThread(event: { threadId: number; resolved: boolean }): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }

        this.exerciseReviewCommentService
            .updateThreadResolvedState(exerciseId, event.threadId, event.resolved)
            .pipe(
                tap((response) => {
                    const updatedThread = response.body;
                    if (!updatedThread?.id) {
                        return;
                    }
                    this.reviewCommentThreads.update((threads) => this.exerciseReviewCommentService.replaceThreadInThreads(threads, updatedThread));
                }),
                catchError(() => {
                    this.alertService.error('artemisApp.review.resolveFailed');
                    return of(null);
                }),
            )
            .subscribe();
    }

    /**
     * Loads all review comment threads for the given exercise.
     *
     * @param exerciseId The exercise to fetch threads for.
     */
    private loadReviewCommentThreads(exerciseId: number): void {
        this.exerciseReviewCommentService
            .getThreads(exerciseId)
            .pipe(
                map((response) => response.body ?? []),
                catchError(() => {
                    this.alertService.error('artemisApp.review.loadFailed');
                    return of([] as CommentThread[]);
                }),
            )
            .subscribe((threads) => this.reviewCommentThreads.set(threads));
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

    onNavigateReviewThread(thread: CommentThread): void {
        this.navigateToLocation({
            targetType: thread.targetType,
            filePath: thread.filePath ?? thread.initialFilePath,
            lineNumber: thread.lineNumber ?? thread.initialLineNumber,
            auxiliaryRepositoryId: thread.auxiliaryRepositoryId,
        });
    }

    private navigateToLocation(location: { targetType: CommentThreadLocationType; filePath?: string; lineNumber?: number; auxiliaryRepositoryId?: number }): void {
        if (location.targetType === CommentThreadLocationType.PROBLEM_STATEMENT) {
            this.codeEditorContainer.selectedFile = this.codeEditorContainer.problemStatementIdentifier;
            if (location.lineNumber !== undefined) {
                this.editableInstructions.jumpToLine(location.lineNumber);
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
                case CommentThreadLocationType.AUXILIARY_REPO:
                    if (this.codeEditorContainer.selectedRepository() !== RepositoryType.AUXILIARY && location.auxiliaryRepositoryId) {
                        this.selectAuxiliaryRepository(location.auxiliaryRepositoryId);
                        return;
                    }
                    break;
                default:
            }
        } catch {
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
                this.fileToJumpOn = undefined;
                return;
            }

            // Will load file and signal to fileLoad when finished loading
            this.codeEditorContainer.selectedFile = this.fileToJumpOn;
            this.fileToJumpOn = undefined;
        }
    }

    /**
     * Performs a deferred jump to a specific line after a file has finished loading.
     *
     * @param {string} fileName
     *        The name of the file that was just loaded.
     */
    onFileLoad(fileName: string) {
        if (this.lineJumpOnFileLoad && this.fileToJumpOn === fileName) {
            this.codeEditorContainer.jumpToLine(this.lineJumpOnFileLoad);
            this.lineJumpOnFileLoad = undefined;
        }
    }

    /**
     * Creates a new thread with an initial user comment.
     *
     * @param targetType The thread location type.
     * @param filePath The file path for the thread.
     * @param lineNumber The line number for the thread.
     * @param text The initial comment text.
     * @param auxiliaryRepositoryId The auxiliary repository id, if applicable.
     */
    private createThreadWithInitialComment(targetType: CommentThreadLocationType, filePath: string, lineNumber: number, text: string, auxiliaryRepositoryId?: number): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }

        const createComment: CreateComment = { contentType: 'USER', text };
        const createThread: CreateCommentThread = {
            targetType,
            initialFilePath: targetType === CommentThreadLocationType.PROBLEM_STATEMENT ? undefined : filePath,
            initialLineNumber: lineNumber,
            auxiliaryRepositoryId,
            initialComment: createComment,
        };

        this.exerciseReviewCommentService
            .createThread(exerciseId, createThread)
            .pipe(
                map((response) => response.body),
                tap((thread) => {
                    if (!thread?.id) {
                        return;
                    }
                    const newThread: CommentThread = thread.comments ? thread : { ...thread, comments: [] };
                    this.reviewCommentThreads.update((threads) => this.exerciseReviewCommentService.appendThreadToThreads(threads, newThread));
                }),
                catchError(() => {
                    this.alertService.error('artemisApp.review.saveFailed');
                    return of(null);
                }),
            )
            .subscribe();
    }
}
