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
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AlertService } from 'app/foundation/service/alert.service';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Observable, tap } from 'rxjs';
import { ProblemStatementAiOperationsHelper } from 'app/programming/manage/shared/problem-statement-ai-operations.helper';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ConsistencyCheckService } from 'app/programming/manage/consistency-check/consistency-check.service';
import { ArtemisIntelligenceService } from 'app/editor/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { ConsistencyCheckError } from 'app/programming/shared/entities/consistency-check-result.model';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { HyperionExerciseAdaptationService } from 'app/hyperion/services/hyperion-exercise-adaptation.service';
import { HyperionExerciseGenerationComponent } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.component';
import { AUTO_START_EXERCISE_GENERATION_STATE } from 'app/hyperion/exercise-generation/exercise-generation.constants';
import { Router } from '@angular/router';
import { ReviewAdaptExerciseDialogComponent, ReviewAdaptExerciseDialogResult } from 'app/exercise/review/adapt-exercise-dialog/review-adapt-exercise-dialog.component';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommentType } from 'app/exercise/shared/entities/review/comment.model';
import { CommentContent, CommentContentType, ConsistencyIssueCommentContent } from 'app/exercise/shared/entities/review/comment-content.model';
import { CommentThread, CommentThreadLocationType, ReviewThreadLocation } from 'app/exercise/shared/entities/review/comment-thread.model';
import { combineAdaptFeedback, getFirstCommentByCreatedDateThenId, selectedThreadsFindingsText } from 'app/exercise/review/review-comment-utils';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
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

const SEVERITY_ORDER: Record<ConsistencyIssue.SeverityEnum, number> = {
    [ConsistencyIssue.SeverityEnum.High]: 0,
    [ConsistencyIssue.SeverityEnum.Medium]: 1,
    [ConsistencyIssue.SeverityEnum.Low]: 2,
};

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
    // Keep review comment state scoped to each editor container instance. DialogService backs the free "Adapt exercise" dialog.
    providers: [ExerciseReviewCommentService, DialogService],
    imports: [
        FaIconComponent,
        HyperionExerciseGenerationComponent,
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
    readonly resultComp = viewChild(UpdatingResultComponent);
    readonly editableInstructions = viewChild(ProgrammingExerciseEditableInstructionComponent);
    /** The embedded Artemis Intelligence run card; reattached after an adapt start so its progress shows on the same surface that triggered it. */
    readonly generationCard = viewChild(HyperionExerciseGenerationComponent);

    /**
     * Whether the embedded run card should auto-start a generation run on load. Set from router state by the create flow's "Generate entire exercise", which navigates here (not to
     * the read-only detail page) so the instructor watches the run stream live in the editor where they will review and refine the result.
     */
    protected readonly autoStartGeneration = signal(false);

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

    /** How many of the review comments the instructor has selected for AI ("Apply with AI") are adaptable findings — gates the "Adapt with N selected comments" menu action. */
    protected readonly selectedAdaptableCommentCount = computed(
        () => this.exerciseReviewCommentService.selectedFeedbackThreads().filter((thread) => this.extractConsistencyIssueContent(thread) !== undefined).length,
    );

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
    private hyperionExerciseAdaptationService = inject(HyperionExerciseAdaptationService);
    private dialogService = inject(DialogService);
    private destroyRef = inject(DestroyRef);
    private adaptDialogRef?: DynamicDialogRef;

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

    protected readonly faSpinner = faSpinner;
    protected readonly facArtemisIntelligence = facArtemisIntelligence;

    protected readonly RepositoryType = RepositoryType;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly faCheckDouble = faCheckDouble;

    constructor() {
        super();
        // Capture the navigation state now (only available during the current navigation): the create flow's "Generate entire exercise" routes here with this flag so the embedded
        // run card auto-starts and the instructor sees the run stream live in the editor.
        const navigationState = inject(Router).currentNavigation()?.extras.state;
        if (navigationState?.[AUTO_START_EXERCISE_GENERATION_STATE] === true) {
            this.autoStartGeneration.set(true);
        }
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

    /**
     * Cleans up AI resources on component teardown.
     */
    override ngOnDestroy() {
        this.adaptDialogRef?.close();
        this.aiOps.destroy();
        super.ngOnDestroy();
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
     * Triggers an Artemis Intelligence adaptation run for the current exercise using the feedback assembled in the review thread widget.
     *
     * The run is keyed by exercise id on the server and surfaces live in the embedded Artemis Intelligence run card in this editor (which reattaches
     * to the live run via its status endpoint), so the instructor sees progress in the same place they triggered it.
     *
     * @param payload The assembled feedback prompt to address.
     */
    onAdaptExercise(payload: { feedback: string }): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId) {
            return;
        }
        this.startAdaptation(exerciseId, payload.feedback);
    }

    /**
     * Starts an adaptation run and, once the server has acknowledged the start, tells the embedded run card to reattach so the same surface
     * that triggered the run shows its live progress and terminal verdict. The card's {@code generationCompleted} output (wired in the template)
     * then drives the existing thread reload. Both adapt entry points (thread widget and free-adapt menu) funnel through here so the reattach
     * behaviour is identical. Subscribed exactly once (the service stream is cold) so only one run is started.
     */
    private startAdaptation(exerciseId: number, feedback: string): void {
        this.hyperionExerciseAdaptationService
            .adaptExercise(exerciseId, feedback)
            ?.pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                // The job is registered server-side by the time next fires, so the card's /status reprobe will see and stream it.
                next: () => this.generationCard()?.reattach(),
                // Errors are already surfaced as alerts by the service; nothing to reattach to.
                error: () => {},
            });
    }

    /**
     * Opens the finding-free "Adapt exercise" dialog from the Artemis Intelligence menu.
     *
     * This reuses the same dialog as the review-thread adapt path, but without a finding: the instructions are required. On confirm, it starts an
     * adaptation run keyed by exercise id; the embedded run card picks up live progress.
     */
    openFreeAdaptDialog(): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId || !this.exercise?.isAtLeastEditor || !this.hyperionEnabled) {
            return;
        }
        this.adaptDialogRef =
            this.dialogService.open(ReviewAdaptExerciseDialogComponent, {
                header: this.translateService.instant('artemisApp.review.adaptExercise.title'),
                modal: true,
                closable: true,
                closeOnEscape: true,
                width: '40vw',
                // No findingText: the dialog renders its finding-free variant with required instructions.
                data: {},
            }) ?? undefined;
        this.adaptDialogRef?.onClose.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((result?: ReviewAdaptExerciseDialogResult) => {
            const instructions = result?.instructions?.trim();
            if (!instructions) {
                return;
            }
            this.startAdaptation(exerciseId, instructions);
        });
    }

    /**
     * Adapts the exercise from SEVERAL selected review comments at once: opens the adapt dialog showing the combined findings and lets the instructor add extra instructions, then
     * starts one adaptation run addressing all of them. Wires up the previously inert "select comment for AI" multi-selection.
     */
    adaptWithSelectedComments(): void {
        const exerciseId = this.exercise?.id;
        if (!exerciseId || !this.exercise?.isAtLeastEditor || !this.hyperionEnabled) {
            return;
        }
        const findingsText = selectedThreadsFindingsText(this.exerciseReviewCommentService.selectedFeedbackThreads(), this.translateService);
        if (!findingsText) {
            return;
        }
        this.adaptDialogRef =
            this.dialogService.open(ReviewAdaptExerciseDialogComponent, {
                header: this.translateService.instant('artemisApp.review.adaptExercise.title'),
                modal: true,
                closable: true,
                closeOnEscape: true,
                width: '40vw',
                // The combined findings are shown read-only as the feedback to address; the instructor's typed instructions are optional and sent in addition.
                data: { findingText: findingsText },
            }) ?? undefined;
        this.adaptDialogRef?.onClose.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((result?: ReviewAdaptExerciseDialogResult) => {
            if (!result) {
                return; // dialog cancelled
            }
            this.startAdaptation(exerciseId, combineAdaptFeedback(findingsText, result.instructions, this.translateService));
        });
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

        try {
            const codeEditorContainer = this.codeEditorContainer()!;
            switch (location.targetType) {
                case CommentThreadLocationType.TEMPLATE_REPO:
                    if (codeEditorContainer.selectedRepository() !== RepositoryType.TEMPLATE) {
                        this.selectTemplateParticipation();
                        return;
                    }
                    break;
                case CommentThreadLocationType.SOLUTION_REPO:
                    if (codeEditorContainer.selectedRepository() !== RepositoryType.SOLUTION) {
                        this.selectSolutionParticipation();
                        return;
                    }
                    break;
                case CommentThreadLocationType.TEST_REPO:
                    if (codeEditorContainer.selectedRepository() !== RepositoryType.TESTS) {
                        this.selectTestRepository();
                        return;
                    }
                    break;
                case CommentThreadLocationType.AUXILIARY_REPO: {
                    const auxiliaryRepositoryId = location.auxiliaryRepositoryId;
                    if (
                        auxiliaryRepositoryId !== undefined &&
                        (codeEditorContainer.selectedRepository() !== RepositoryType.AUXILIARY || this.selectedRepositoryId !== auxiliaryRepositoryId)
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
