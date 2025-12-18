import { AfterViewChecked, Component, OnDestroy, ViewChild, computed, inject, signal } from '@angular/core';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { CodeEditorInstructorBaseContainerComponent } from 'app/programming/manage/code-editor/instructor-and-editor-container/code-editor-instructor-base-container.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faBan, faCircleNotch, faPlus, faSave, faSpinner, faTimes, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
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
import { Subscription, catchError, finalize, of, take } from 'rxjs';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faCheckDouble } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { InlineCommentService } from 'app/shared/monaco-editor/service/inline-comment.service';
import { InlineComment } from 'app/shared/monaco-editor/model/inline-comment.model';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ProblemStatementRefinementRequest } from 'app/openapi/model/problemStatementRefinementRequest';
import { InlineComment as ApiInlineComment } from 'app/openapi/model/inlineComment';
import { MarkdownDiffEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-diff-editor-monaco.component';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TaskAction } from 'app/shared/monaco-editor/model/actions/task.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { HyperionCodeGenerationApiService } from 'app/openapi/api/hyperionCodeGenerationApi.service';

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
        MarkdownDiffEditorMonacoComponent,
    ],
})
export class CodeEditorInstructorAndEditorContainerComponent extends CodeEditorInstructorBaseContainerComponent implements OnDestroy, AfterViewChecked {
    @ViewChild(UpdatingResultComponent, { static: false }) resultComp: UpdatingResultComponent;
    @ViewChild(ProgrammingExerciseEditableInstructionComponent, { static: false }) editableInstructions: ProgrammingExerciseEditableInstructionComponent;
    @ViewChild('diffEditor') diffEditor?: MarkdownDiffEditorMonacoComponent;

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly consistencyIssues = signal<ConsistencyIssue[]>([]);

    private consistencyCheckService = inject(ConsistencyCheckService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);
    private profileService = inject(ProfileService);
    private inlineCommentService = inject(InlineCommentService);
    private hyperionApiService = inject(HyperionProblemStatementApiService);

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;
    faSave = faSave;
    faBan = faBan;

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

    // Inline comment state
    protected pendingComments = this.inlineCommentService.getPendingComments();
    protected pendingCount = this.inlineCommentService.pendingCount;
    protected hasPendingComments = this.inlineCommentService.hasPendingComments;
    protected applyingCommentId = signal<string | undefined>(undefined);
    protected isApplyingAll = signal(false);
    protected isAnyApplying = computed(() => !!this.applyingCommentId() || this.isApplyingAll());
    private currentRefinementSubscription: Subscription | undefined;
    private exerciseContextInitialized = false;
    private lastExerciseId: number | undefined;

    // Diff mode properties
    showDiff = false;
    originalProblemStatement = '';
    refinedProblemStatement = '';
    private diffContentSet = false;

    // Domain actions for diff editor toolbar
    private readonly testCaseAction: TextEditorDomainAction = new TestCaseAction();
    domainActions: TextEditorDomainAction[] = [new FormulaAction(), new TaskAction(), this.testCaseAction];
    metaActions: TextEditorAction[] = [new FullscreenAction()];

    /**
     * Lifecycle hook called after every check of the component's view.
     * Used to set diff editor content when it becomes available.
     */
    ngAfterViewChecked(): void {
        if (this.showDiff && this.diffEditor && !this.diffContentSet) {
            this.diffEditor.setFileContents(this.originalProblemStatement, this.refinedProblemStatement, 'original.md', 'refined.md');
            this.diffContentSet = true;
        }
    }

    /**
     * Override applyDomainChange to initialize inline comment service after exercise is loaded.
     */
    protected override applyDomainChange(domainType: any, domainValue: any): void {
        super.applyDomainChange(domainType, domainValue);

        // Initialize inline comment service with exercise context (reinitialize if exercise changes)
        if (this.exercise?.id && (this.lastExerciseId !== this.exercise.id || !this.exerciseContextInitialized)) {
            this.inlineCommentService.setExerciseContext(this.exercise.id);
            this.lastExerciseId = this.exercise.id;
            this.exerciseContextInitialized = true;
        }
    }

    override ngOnDestroy(): void {
        super.ngOnDestroy();
        this.currentRefinementSubscription?.unsubscribe();
        // Clear inline comment context when leaving the page
        this.inlineCommentService.clearContext();
        // Reset context tracking so it reinitializes on component reuse
        this.exerciseContextInitialized = false;
        this.lastExerciseId = undefined;
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
            this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
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
                            this.alertService.success(this.translateService.instant('artemisApp.consistencyCheck.noInconsistencies'));
                        } else {
                            this.alertService.warning(this.translateService.instant('artemisApp.consistencyCheck.inconsistenciesFoundAlert'));
                        }
                    },
                    error: () => {
                        this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
                    },
                });
            },
            error: () => {
                this.alertService.error(this.translateService.instant('artemisApp.consistencyCheck.checkFailedAlert'));
            },
        });
    }

    // Diff Editor Methods

    /**
     * Accepts the refined problem statement and applies the changes.
     */
    acceptRefinement(): void {
        if (this.refinedProblemStatement) {
            this.exercise.problemStatement = this.refinedProblemStatement;
            this.editableInstructions?.updateProblemStatement(this.refinedProblemStatement);
            this.closeDiff();
            this.alertService.success('artemisApp.programmingExercise.problemStatement.changesApplied');
        }
    }

    /**
     * Rejects the refined problem statement and keeps the original.
     */
    rejectRefinement(): void {
        this.closeDiff();
    }

    /**
     * Closes the diff view and resets diff state.
     */
    closeDiff(): void {
        this.showDiff = false;
        this.originalProblemStatement = '';
        this.refinedProblemStatement = '';
        this.diffContentSet = false;
    }

    // Inline Comment Methods

    /**
     * Handles saving an inline comment (adds to pending list).
     */
    onSaveInlineComment(comment: InlineComment): void {
        const existingComment = this.inlineCommentService.getComment(comment.id);
        if (existingComment) {
            // Update existing comment's status
            this.inlineCommentService.updateStatus(comment.id, 'pending');
        } else {
            // Add new comment
            this.inlineCommentService.addExistingComment({ ...comment, status: 'pending' });
        }
    }

    /**
     * Handles applying an inline comment immediately with AI.
     */
    onApplyInlineComment(comment: InlineComment): void {
        // First add to service if not already there
        if (!this.inlineCommentService.getComment(comment.id)) {
            this.inlineCommentService.addExistingComment(comment);
        }
        this.applySingleComment(comment);
    }

    /**
     * Handles deleting an inline comment.
     */
    onDeleteInlineComment(commentId: string): void {
        this.inlineCommentService.removeComment(commentId);
    }

    /**
     * Cancels the current inline comment apply operation.
     */
    onCancelInlineCommentApply(): void {
        if (this.currentRefinementSubscription) {
            this.currentRefinementSubscription.unsubscribe();
            this.currentRefinementSubscription = undefined;
        }

        const commentId = this.applyingCommentId();
        if (commentId) {
            this.inlineCommentService.updateStatus(commentId, 'pending');
        }
        this.applyingCommentId.set(undefined);
        this.isApplyingAll.set(false);
    }

    /**
     * Clears all pending inline comments.
     */
    clearAllComments(): void {
        this.inlineCommentService.clearAll();
    }

    /**
     * Applies all pending inline comments.
     */
    applyAllComments(): void {
        const comments = this.pendingComments();
        if (comments.length === 0) {
            return;
        }

        const courseId = this.exercise?.course?.id ?? this.exercise?.exerciseGroup?.exam?.course?.id;
        if (!courseId || !this.exercise?.problemStatement?.trim()) {
            this.alertService.error('artemisApp.programmingExercise.inlineComment.applyError');
            return;
        }

        this.isApplyingAll.set(true);

        // Mark all comments as applying
        for (const comment of comments) {
            this.inlineCommentService.updateStatus(comment.id, 'applying');
        }

        const apiComments: ApiInlineComment[] = comments.map((comment) => ({
            startLine: comment.startLine,
            endLine: comment.endLine,
            instruction: comment.instruction,
        }));

        const request: ProblemStatementRefinementRequest = {
            problemStatementText: this.exercise.problemStatement,
            inlineComments: apiComments,
        };

        this.currentRefinementSubscription = this.hyperionApiService
            .refineProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.isApplyingAll.set(false);
                    this.currentRefinementSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        this.originalProblemStatement = this.exercise.problemStatement || '';
                        this.refinedProblemStatement = response.refinedProblemStatement;
                        this.diffContentSet = false;
                        this.showDiff = true;

                        this.inlineCommentService.markAllApplied(comments.map((c) => c.id));
                        this.alertService.success('artemisApp.programmingExercise.inlineComment.applyAllSuccess');
                    } else {
                        for (const comment of comments) {
                            this.inlineCommentService.updateStatus(comment.id, 'error');
                        }
                        this.alertService.error('artemisApp.programmingExercise.inlineComment.applyError');
                    }
                },
                error: () => {
                    for (const comment of comments) {
                        this.inlineCommentService.updateStatus(comment.id, 'error');
                    }
                    this.alertService.error('artemisApp.programmingExercise.inlineComment.applyError');
                },
            });
    }

    /**
     * Applies a single inline comment using AI refinement.
     */
    private applySingleComment(comment: InlineComment): void {
        const courseId = this.exercise?.course?.id ?? this.exercise?.exerciseGroup?.exam?.course?.id;

        if (!courseId || this.exercise?.problemStatement == null) {
            this.alertService.error('artemisApp.programmingExercise.inlineComment.applyError');
            return;
        }

        this.applyingCommentId.set(comment.id);
        this.inlineCommentService.updateStatus(comment.id, 'applying');

        const apiComment: ApiInlineComment = {
            startLine: comment.startLine,
            endLine: comment.endLine,
            instruction: comment.instruction,
        };

        const request: ProblemStatementRefinementRequest = {
            problemStatementText: this.exercise.problemStatement,
            inlineComments: [apiComment],
        };

        this.currentRefinementSubscription = this.hyperionApiService
            .refineProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.applyingCommentId.set(undefined);
                    this.currentRefinementSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        // Store original and refined content for diff view
                        this.originalProblemStatement = this.exercise.problemStatement || '';
                        this.refinedProblemStatement = response.refinedProblemStatement;
                        this.diffContentSet = false;
                        this.showDiff = true;

                        // Mark comment as applied and remove from pending
                        this.inlineCommentService.markApplied(comment.id);
                        this.alertService.success('artemisApp.programmingExercise.inlineComment.applySuccess');
                    } else {
                        this.inlineCommentService.updateStatus(comment.id, 'error');
                        this.alertService.error('artemisApp.programmingExercise.inlineComment.applyError');
                    }
                },
                error: () => {
                    this.inlineCommentService.updateStatus(comment.id, 'error');
                    this.alertService.error('artemisApp.programmingExercise.inlineComment.applyError');
                },
            });
    }
}
