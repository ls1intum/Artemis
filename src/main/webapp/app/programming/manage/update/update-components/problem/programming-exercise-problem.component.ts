import { AfterViewChecked, Component, EventEmitter, Input, OnDestroy, OnInit, Output, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { faBan, faQuestionCircle, faSave, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbAlert } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { PopoverModule } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ProblemStatementGenerationRequest } from 'app/openapi/model/problemStatementGenerationRequest';
import { ProblemStatementRefinementRequest } from 'app/openapi/model/problemStatementRefinementRequest';
import { InlineComment as ApiInlineComment } from 'app/openapi/model/inlineComment';
import { Subscription, finalize } from 'rxjs';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FileService } from 'app/shared/service/file.service';
import { MarkdownDiffEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-diff-editor-monaco.component';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { FullscreenAction } from 'app/shared/monaco-editor/model/actions/fullscreen.action';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TaskAction } from 'app/shared/monaco-editor/model/actions/task.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { InlineCommentService } from 'app/shared/monaco-editor/service/inline-comment.service';
import { InlineComment } from 'app/shared/monaco-editor/model/inline-comment.model';

@Component({
    selector: 'jhi-programming-exercise-problem',
    templateUrl: './programming-exercise-problem.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [
        TranslateDirective,
        NgbAlert,
        ProgrammingExerciseInstructionComponent,
        ProgrammingExerciseEditableInstructionComponent,
        CompetencySelectionComponent,
        FormsModule,
        ArtemisTranslatePipe,
        PopoverModule,
        ButtonModule,
        FaIconComponent,
        HelpIconComponent,
        MarkdownDiffEditorMonacoComponent,
    ],
})
export class ProgrammingExerciseProblemComponent implements OnInit, OnDestroy, AfterViewChecked {
    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly ProjectType = ProjectType;
    protected readonly AssessmentType = AssessmentType;
    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    protected readonly faQuestionCircle = faQuestionCircle;

    @Input({ required: true }) programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();
    programmingExercise = input<ProgrammingExercise>();
    @Output() problemStatementChange = new EventEmitter<string>();
    programmingExerciseChange = output<ProgrammingExercise>();
    // Problem statement generation properties
    userPrompt = '';
    isGenerating = false;
    private currentGenerationSubscription: Subscription | undefined = undefined;
    private profileService = inject(ProfileService);
    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);

    // icons
    facArtemisIntelligence = facArtemisIntelligence;
    faSpinner = faSpinner;
    faBan = faBan;
    faSave = faSave;

    // Injected services
    private hyperionApiService = inject(HyperionProblemStatementApiService);
    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);

    /**
     * Lifecycle hook that is called when the component is destroyed.
     * Cleans up the subscription to prevent memory leaks.
     */
    ngOnDestroy(): void {
        if (this.currentGenerationSubscription) {
            this.currentGenerationSubscription.unsubscribe();
            this.currentGenerationSubscription = undefined;
        }
    }

    /**
     * Handles problem statement changes from the editor
     */
    onProblemStatementChange(newProblemStatement: string): void {
        const exercise = this.programmingExercise();
        this.currentProblemStatement.set(newProblemStatement);
        this.programmingExerciseCreationConfig.hasUnsavedChanges = true;
        if (exercise) {
            exercise.problemStatement = newProblemStatement;
            this.programmingExerciseChange.emit(exercise);
        }
        this.problemStatementChange.emit(newProblemStatement);
    }

    get exerciseId(): number | undefined {
        const exercise = this.programmingExercise();
        return exercise?.id;
    }
    isRefining = false;

    // Diff mode properties
    showDiff = false;
    originalProblemStatement = '';
    refinedProblemStatement = '';
    private diffContentSet = false;
    diffEditor = viewChild<MarkdownDiffEditorMonacoComponent>('diffEditor');
    private templateProblemStatement = signal<string>('');
    private currentProblemStatement = signal<string>('');
    private readonly testCaseAction: TextEditorDomainAction = new TestCaseAction();
    domainActions: TextEditorDomainAction[] = [new FormulaAction(), new TaskAction(), this.testCaseAction];
    metaActions: TextEditorAction[] = [new FullscreenAction()];

    // Injected services
    private fileService = inject(FileService);
    private inlineCommentService = inject(InlineCommentService);

    // Inline comment state
    protected pendingComments = this.inlineCommentService.getPendingComments();
    protected hasPendingComments = this.inlineCommentService.hasPendingComments;
    protected pendingCount = this.inlineCommentService.pendingCount;
    protected applyingCommentId = signal<string | undefined>(undefined);
    protected isApplyingAll = signal(false);

    /**
     * Lifecycle hook called after every check of the component's view.
     * Used to set diff editor content when it becomes available.
     */
    ngAfterViewChecked(): void {
        const editor = this.diffEditor();
        if (this.showDiff && editor && !this.diffContentSet) {
            editor.setFileContents(this.originalProblemStatement, this.refinedProblemStatement, 'original.md', 'refined.md');
            this.diffContentSet = true;
        }
    }

    /**
     * Lifecycle hook to capture the initial problem statement for comparison.
     * Also loads the template problem statement for robust comparison.
     */
    ngOnInit(): void {
        const exercise = this.programmingExercise();

        // Initialize current problem statement
        if (exercise?.problemStatement) {
            this.currentProblemStatement.set(exercise.problemStatement);
        }

        // Load the template problem statement for comparison
        if (exercise?.programmingLanguage) {
            this.fileService.getTemplateFile(exercise.programmingLanguage, exercise.projectType).subscribe({
                next: (template) => {
                    this.templateProblemStatement.set(template);
                },
            });
        }

        // Initialize inline comment service with exercise context
        if (exercise?.id) {
            this.inlineCommentService.setExerciseContext(exercise.id);
        }
    }

    /**
     * Cancels the ongoing problem statement generation, refinement, or inline comment application.
     * Preserves the user's prompt so they can retry or modify it.
     * Resets all in-progress states including inline comment statuses.
     */
    cancelGeneration(): void {
        if (this.currentGenerationSubscription) {
            this.currentGenerationSubscription.unsubscribe();
            this.currentGenerationSubscription = undefined;
        }

        // Reset global generation/refinement flags
        this.isGenerating = false;
        this.isRefining = false;

        // Reset inline comment application states
        const wasApplyingSingle = this.applyingCommentId() !== undefined;
        const wasApplyingAll = this.isApplyingAll();

        if (wasApplyingSingle) {
            const commentId = this.applyingCommentId();
            this.applyingCommentId.set(undefined);
            if (commentId) {
                // Revert the comment status from 'applying' back to 'pending'
                this.inlineCommentService.updateStatus(commentId, 'pending');
            }
        }

        if (wasApplyingAll) {
            this.isApplyingAll.set(false);
            // Revert all comments that were 'applying' back to 'pending'
            const comments = this.pendingComments();
            comments.forEach((c) => {
                if (c.status === 'applying') {
                    this.inlineCommentService.updateStatus(c.id, 'pending');
                }
            });
        }
    }

    /**
     * Normalizes a string by trimming whitespace and normalizing line endings.
     * This helps compare problem statements that might have formatting differences.
     */
    private normalizeString(str: string): string {
        if (!str) return '';
        // Normalize line endings to \n and trim
        return str.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim();
    }

    /**
     * Computed signal that determines whether to show the generate or refine button.
     * Reacts to changes in the problem statement.
     */
    shouldShowGenerateButton = computed(() => {
        const problemStatement = this.currentProblemStatement();
        const template = this.templateProblemStatement();

        // Show generate button if problem statement is empty or only whitespace
        if (!problemStatement || problemStatement.trim() === '') {
            return true;
        }

        // Normalize both strings for comparison to handle whitespace/line ending differences
        const normalizedProblemStatement = this.normalizeString(problemStatement);
        const normalizedTemplate = this.normalizeString(template);

        // Compare against the loaded template problem statement
        return !!(normalizedTemplate && normalizedProblemStatement === normalizedTemplate);
    });

    /**
     * Generates a draft problem statement using the user's prompt
     */
    generateProblemStatement(): void {
        const exercise = this.programmingExercise();
        const courseId = exercise?.course?.id ?? exercise?.exerciseGroup?.exam?.course?.id;
        if (!this.userPrompt?.trim() || !courseId) {
            return;
        }

        this.isGenerating = true;

        const request: ProblemStatementGenerationRequest = {
            userPrompt: this.userPrompt.trim(),
        };

        this.currentGenerationSubscription = this.hyperionApiService
            .generateProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.isGenerating = false;
                    this.currentGenerationSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    // Check if the response contains an empty or invalid problem statement
                    if (!response.draftProblemStatement || response.draftProblemStatement.trim() === '') {
                        this.alertService.error('artemisApp.programmingExercise.problemStatement.generationError');
                        return;
                    }

                    if (response.draftProblemStatement && exercise) {
                        exercise.problemStatement = response.draftProblemStatement;
                        this.currentProblemStatement.set(response.draftProblemStatement);
                        this.programmingExerciseCreationConfig.hasUnsavedChanges = true;
                        this.problemStatementChange.emit(response.draftProblemStatement);
                        this.programmingExerciseChange.emit(exercise);
                    }
                    this.userPrompt = '';

                    // Show success alert
                    this.alertService.success('artemisApp.programmingExercise.problemStatement.generationSuccess');
                },
                error: (error) => {
                    this.alertService.error('artemisApp.programmingExercise.problemStatement.generationError');
                },
            });
    }

    /**
     * Refines an existing problem statement using the user's prompt
     */
    refineProblemStatement(): void {
        const exercise = this.programmingExercise();
        const courseId = exercise?.course?.id ?? exercise?.exerciseGroup?.exam?.course?.id;
        if (!this.userPrompt?.trim() || !courseId || !exercise?.problemStatement) {
            return;
        }

        this.isRefining = true;

        const request: ProblemStatementRefinementRequest = {
            problemStatementText: exercise.problemStatement,
            userPrompt: this.userPrompt.trim(),
        };

        this.currentGenerationSubscription = this.hyperionApiService
            .refineProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.isRefining = false;
                    this.currentGenerationSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    // Check if refinement was successful
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        // Store original and refined content for diff view
                        this.originalProblemStatement = exercise.problemStatement || '';
                        this.refinedProblemStatement = response.refinedProblemStatement;
                        this.diffContentSet = false; // Reset flag so content will be set in ngAfterViewChecked
                        this.showDiff = true;
                        this.userPrompt = '';
                    } else if (response.originalProblemStatement) {
                        // Refinement failed: keep the original problem statement
                        this.alertService.warning('artemisApp.programmingExercise.problemStatement.refinementFailed');
                    } else {
                        this.alertService.error('artemisApp.programmingExercise.problemStatement.refinementError');
                    }
                },
                error: (error) => {
                    // Preserve original problem statement from error parameters
                    const originalFromError = error?.error?.params?.originalProblemStatement;
                    if (originalFromError && exercise) {
                        // Restore original statement if it was included in error
                        exercise.problemStatement = originalFromError;
                        this.currentProblemStatement.set(originalFromError);
                        this.programmingExerciseChange.emit(exercise);
                    }
                    this.alertService.error('artemisApp.programmingExercise.problemStatement.refinementError');
                },
            });
    }

    /**
     * Accepts the refined problem statement and applies the changes
     */
    acceptRefinement(): void {
        const exercise = this.programmingExercise();
        if (exercise && this.refinedProblemStatement) {
            exercise.problemStatement = this.refinedProblemStatement;
            this.programmingExerciseCreationConfig.hasUnsavedChanges = true;
            this.problemStatementChange.emit(this.refinedProblemStatement);
            this.programmingExerciseChange.emit(exercise);
            this.currentProblemStatement.set(this.refinedProblemStatement);
            this.closeDiff();
        }
    }

    /**
     * Rejects the refined problem statement and keeps the original
     */
    rejectRefinement(): void {
        this.closeDiff();
    }

    /**
     * Closes the diff view and resets diff state
     */
    closeDiff(): void {
        this.showDiff = false;
        this.originalProblemStatement = '';
        this.refinedProblemStatement = '';
        this.diffContentSet = false;
    }

    /**
     * Handles the button click - either generates or refines based on current state
     */
    handleProblemStatementAction(): void {
        if (this.shouldShowGenerateButton()) {
            this.generateProblemStatement();
        } else {
            this.refineProblemStatement();
        }
    }

    protected readonly ButtonSize = ButtonSize;

    /**
     * Get the translated example placeholder text for the input field
     */
    getTranslatedPlaceholder(): string {
        return this.translateService.instant('artemisApp.programmingExercise.problemStatement.examplePlaceholder');
    }

    /**
     * Handles changes to competency links
     */
    onCompetencyLinksChange(competencyLinks: any): void {
        const exercise = this.programmingExercise();
        if (exercise) {
            exercise.competencyLinks = competencyLinks;
            this.programmingExerciseChange.emit(exercise);
        }
    }

    // Inline Comment Methods

    /**
     * Applies a single inline comment using AI refinement.
     */
    applySingleComment(comment: InlineComment): void {
        const exercise = this.programmingExercise();
        const courseId = exercise?.course?.id ?? exercise?.exerciseGroup?.exam?.course?.id;

        if (!courseId || !exercise?.problemStatement) {
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
            problemStatementText: exercise.problemStatement,
            inlineComments: [apiComment],
        };

        this.currentGenerationSubscription = this.hyperionApiService
            .refineProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.applyingCommentId.set(undefined);
                    this.currentGenerationSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        // Store original and refined content for diff view
                        this.originalProblemStatement = exercise.problemStatement || '';
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

    /**
     * Applies all pending inline comments in batch using AI refinement.
     */
    applyAllComments(): void {
        const exercise = this.programmingExercise();
        const courseId = exercise?.course?.id ?? exercise?.exerciseGroup?.exam?.course?.id;
        const comments = this.pendingComments();

        if (!courseId || !exercise?.problemStatement || comments.length === 0) {
            return;
        }

        this.isApplyingAll.set(true);

        // Update status for all comments
        comments.forEach((c) => this.inlineCommentService.updateStatus(c.id, 'applying'));

        const apiComments: ApiInlineComment[] = comments.map((c) => ({
            startLine: c.startLine,
            endLine: c.endLine,
            instruction: c.instruction,
        }));

        const request: ProblemStatementRefinementRequest = {
            problemStatementText: exercise.problemStatement,
            inlineComments: apiComments,
        };

        this.currentGenerationSubscription = this.hyperionApiService
            .refineProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.isApplyingAll.set(false);
                    this.currentGenerationSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        // Store original and refined content for diff view
                        this.originalProblemStatement = exercise.problemStatement || '';
                        this.refinedProblemStatement = response.refinedProblemStatement;
                        this.diffContentSet = false;
                        this.showDiff = true;

                        // Mark all comments as applied
                        const commentIds = comments.map((c) => c.id);
                        this.inlineCommentService.markAllApplied(commentIds);
                        this.alertService.success('artemisApp.programmingExercise.inlineComment.applyAllSuccess');
                    } else {
                        comments.forEach((c) => this.inlineCommentService.updateStatus(c.id, 'error'));
                        this.alertService.error('artemisApp.programmingExercise.inlineComment.applyAllError');
                    }
                },
                error: () => {
                    comments.forEach((c) => this.inlineCommentService.updateStatus(c.id, 'error'));
                    this.alertService.error('artemisApp.programmingExercise.inlineComment.applyAllError');
                },
            });
    }

    /**
     * Handles saving an inline comment (adds to pending list).
     */
    onSaveInlineComment(comment: InlineComment): void {
        this.inlineCommentService.addExistingComment({ ...comment, status: 'pending' });
    }

    /**
     * Handles applying an inline comment immediately.
     */
    onApplyInlineComment(comment: InlineComment): void {
        // First add to service, then apply
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
     * Clears all pending inline comments.
     */
    clearAllComments(): void {
        this.inlineCommentService.clearAll();
    }
}
