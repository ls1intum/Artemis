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
import { Observable, Subscription, catchError, map, of, switchMap, take, tap, throwError } from 'rxjs';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faCheckDouble } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';
import { ExerciseReviewCommentService } from 'app/exercise/services/exercise-review-comment.service';
import { CommentThread, CommentThreadLocationType, CreateCommentThread } from 'app/exercise/shared/entities/review/comment-thread.model';
import { CommentType, CreateComment } from 'app/exercise/shared/entities/review/comment.model';
import { UserCommentContent } from 'app/exercise/shared/entities/review/comment-content.model';

const PROBLEM_STATEMENT_FILE_PATH = 'problem_statement.md';
import { getRepoPath } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check';

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
        NgbTooltip,
        ArtemisTranslatePipe,
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

    onCommit(): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }
        this.reviewCommentThreads.set([]);
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
        // Clear previous consistency issues
        this.consistencyIssues.set([]);

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

    onSubmitReviewComment(event: { lineNumber: number; fileName: string; text: string }): void {
        const targetType = this.mapRepositoryToThreadLocationType(this.selectedRepository);
        const auxiliaryRepositoryId = this.selectedRepository === RepositoryType.AUXILIARY ? this.selectedRepositoryId : undefined;
        this.createThreadWithInitialComment(targetType, event.fileName, event.lineNumber, event.text, auxiliaryRepositoryId);
    }

    onSubmitProblemStatementReviewComment(event: { lineNumber: number; fileName: string; text: string }): void {
        this.createThreadWithInitialComment(CommentThreadLocationType.PROBLEM_STATEMENT, PROBLEM_STATEMENT_FILE_PATH, event.lineNumber, event.text);
    }

    onDeleteReviewComment(commentId: number): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }

        this.exerciseReviewCommentService
            .deleteComment(exerciseId, commentId)
            .pipe(
                tap(() => {
                    this.reviewCommentThreads.update((threads) =>
                        threads
                            .map((thread) => {
                                if (!thread.comments) {
                                    return thread;
                                }
                                const remainingComments = thread.comments.filter((comment) => comment.id !== commentId);
                                if (remainingComments.length === thread.comments.length) {
                                    return thread;
                                }
                                return { ...thread, comments: remainingComments };
                            })
                            .filter((thread) => !thread.comments || thread.comments.length > 0),
                    );
                }),
                catchError(() => {
                    this.alertService.error('artemisApp.review.deleteFailed');
                    return of(null);
                }),
            )
            .subscribe();
    }

    onReplyReviewComment(event: { threadId: number; text: string }): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }

        const commentContent: UserCommentContent = { contentType: 'USER', text: event.text };
        const createComment: CreateComment = { type: CommentType.USER, content: commentContent };

        this.exerciseReviewCommentService
            .createComment(exerciseId, event.threadId, createComment)
            .pipe(
                tap((response) => {
                    const createdComment = response.body;
                    if (!createdComment?.threadId) {
                        return;
                    }
                    this.reviewCommentThreads.update((threads) =>
                        threads.map((thread) => {
                            if (thread.id !== createdComment.threadId) {
                                return thread;
                            }
                            const comments = thread.comments ?? [];
                            return { ...thread, comments: [...comments, createdComment] };
                        }),
                    );
                }),
                catchError(() => {
                    this.alertService.error('artemisApp.review.saveFailed');
                    return of(null);
                }),
            )
            .subscribe();
    }

    onUpdateReviewComment(event: { commentId: number; text: string }): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }

        const commentContent: UserCommentContent = { contentType: 'USER', text: event.text };
        this.exerciseReviewCommentService
            .updateCommentContent(exerciseId, event.commentId, { content: commentContent })
            .pipe(
                tap((response) => {
                    const updatedComment = response.body;
                    if (!updatedComment?.id || !updatedComment.threadId) {
                        return;
                    }
                    this.reviewCommentThreads.update((threads) =>
                        threads.map((thread) => {
                            if (thread.id !== updatedComment.threadId || !thread.comments) {
                                return thread;
                            }
                            return {
                                ...thread,
                                comments: thread.comments.map((comment) => (comment.id === updatedComment.id ? { ...comment, ...updatedComment } : comment)),
                            };
                        }),
                    );
                }),
                catchError(() => {
                    this.alertService.error('artemisApp.review.saveFailed');
                    return of(null);
                }),
            )
            .subscribe();
    }

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
                    this.reviewCommentThreads.update((threads) => threads.map((thread) => (thread.id === updatedThread.id ? updatedThread : thread)));
                }),
                catchError(() => {
                    this.alertService.error('artemisApp.review.resolveFailed');
                    return of(null);
                }),
            )
            .subscribe();
    }

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

    private mapRepositoryToThreadLocationType(repositoryType: RepositoryType): CommentThreadLocationType {
        switch (repositoryType) {
            case RepositoryType.SOLUTION:
                return CommentThreadLocationType.SOLUTION_REPO;
            case RepositoryType.TESTS:
                return CommentThreadLocationType.TEST_REPO;
            case RepositoryType.AUXILIARY:
                return CommentThreadLocationType.AUXILIARY_REPO;
            default:
                return CommentThreadLocationType.TEMPLATE_REPO;
        }
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

    /**
     * Navigates between issue locations in the dropdown and updates the editor accordingly.
     *
     * If navigating within the same issue, the location index is advanced (with wrap-around).
     * If switching to a new issue, the first or last location is selected based on `deltaIndex`.
     *
     * The method prepares the jump target (file + line), switches repositories if needed,
     * and triggers file loading. If the file is already open, the jump executes immediately;
     * otherwise it runs after the editor’s file-load event.
     *
     * @param {ConsistencyIssue} issue   The issue being navigated.
     * @param {1 | -1} deltaIndex        Direction of navigation (forward or backward).
     * @param {Event} event              The originating UI event.
     */
    onIssueNavigate(issue: ConsistencyIssue, deltaIndex: 1 | -1, event: Event) {
        if (issue === this.selectedIssue) {
            // Stay in bounds of the array
            this.locationIndex = (this.locationIndex + this.selectedIssue.relatedLocations.length + deltaIndex) % this.selectedIssue.relatedLocations.length;
        } else {
            this.selectedIssue = issue;
            this.locationIndex = deltaIndex === 1 ? 0 : issue.relatedLocations.length - 1;
        }

        // We can always jump to the problem statement
        if (issue.relatedLocations[this.locationIndex].type === 'PROBLEM_STATEMENT') {
            this.codeEditorContainer.selectedFile = this.codeEditorContainer.problemStatementIdentifier;
            this.editableInstructions.jumpToLine(issue.relatedLocations[this.locationIndex].endLine);
            return;
        }

        // Set parameters for when fileLoad is called
        this.lineJumpOnFileLoad = issue.relatedLocations[this.locationIndex].endLine;
        this.fileToJumpOn = getRepoPath(issue.relatedLocations[this.locationIndex]);

        // Jump to the right repo
        // This signals onEditorLoaded if successful
        try {
            if (issue.relatedLocations[this.locationIndex].type === 'TEMPLATE_REPOSITORY' && this.codeEditorContainer.selectedRepository() !== 'TEMPLATE') {
                this.selectTemplateParticipation();
                return;
            } else if (issue.relatedLocations[this.locationIndex].type === 'SOLUTION_REPOSITORY' && this.codeEditorContainer.selectedRepository() !== 'SOLUTION') {
                this.selectSolutionParticipation();
                return;
            } else if (issue.relatedLocations[this.locationIndex].type === 'TESTS_REPOSITORY' && this.codeEditorContainer.selectedRepository() !== 'TESTS') {
                this.selectTestRepository();
                return;
            }
        } catch (error) {
            this.alertService.error(this.translateService.instant('artemisApp.hyperion.consistencyCheck.navigationFailed'));
            this.lineJumpOnFileLoad = undefined;
            this.fileToJumpOn = undefined;
            return;
        }

        // We were already in the right repo, no jump, so the editor did not reload
        // So call the function manually
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
        if (this.lineJumpOnFileLoad && this.fileToJumpOn === fileName) {
            this.codeEditorContainer.jumpToLine(this.lineJumpOnFileLoad);
            this.lineJumpOnFileLoad = undefined;
        }
    }

    private createThreadWithInitialComment(targetType: CommentThreadLocationType, filePath: string, lineNumber: number, text: string, auxiliaryRepositoryId?: number): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }

        const createThread: CreateCommentThread = {
            targetType,
            filePath,
            initialFilePath: filePath,
            lineNumber,
            initialLineNumber: lineNumber,
            auxiliaryRepositoryId,
        };
        const commentContent: UserCommentContent = { contentType: 'USER', text };
        const createComment: CreateComment = { type: CommentType.USER, content: commentContent };

        this.exerciseReviewCommentService
            .createThread(exerciseId, createThread)
            .pipe(
                map((response) => response.body),
                switchMap((thread) => {
                    if (!thread?.id) {
                        return throwError(() => new Error('missingThreadId'));
                    }
                    return this.exerciseReviewCommentService.createComment(exerciseId, thread.id, createComment).pipe(map((response) => ({ thread, comment: response.body })));
                }),
                tap(({ thread, comment }) => {
                    if (!thread?.id || !comment) {
                        return;
                    }
                    const newThread: CommentThread = { ...thread, comments: [comment] };
                    this.reviewCommentThreads.update((threads) => [...threads, newThread]);
                }),
                catchError(() => {
                    this.alertService.error('artemisApp.review.saveFailed');
                    return of(null);
                }),
            )
            .subscribe();
    }
}
