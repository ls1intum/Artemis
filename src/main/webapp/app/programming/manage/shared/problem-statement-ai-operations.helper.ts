import { DestroyRef, Injector, Signal, afterNextRender, computed, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ProblemStatementService } from 'app/programming/manage/services/problem-statement.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { InlineRefinementEvent, MAX_USER_PROMPT_LENGTH, PROMPT_LENGTH_WARNING_THRESHOLD, isTemplateOrEmpty } from 'app/programming/manage/shared/problem-statement.utils';
import { LineChange } from 'app/programming/shared/utils/diff.utils';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';

/**
 * Callback interface that consumers implement to propagate state changes
 * when a problem statement is generated, refined, or reverted.
 *
 * This decouples the shared AI operation logic from the specific
 * component lifecycle (outputs vs. base-class methods, etc.).
 */
export interface ProblemStatementChangeHandler {
    /** Called when the problem statement content changes (generation, refinement accept, revert). */
    onContentChanged(content: string, exercise: ProgrammingExercise): void;
}

/**
 * Shared helper that encapsulates all AI-powered problem statement operations:
 * generation, global refinement, targeted (inline) refinement, diff management,
 * template loading, and cancellation.
 *
 * Both `ProgrammingExerciseProblemComponent` and `CodeEditorInstructorAndEditorContainerComponent`
 * delegate to an instance of this helper to eliminate code duplication.
 */
export class ProblemStatementAiOperationsHelper {
    /** User's prompt text (generation or refinement instruction). */
    readonly userPrompt = signal('');

    /** Whether a generation or refinement HTTP call is in flight. */
    readonly isGeneratingOrRefining = signal(false);

    /** Whether the prompt length is near the maximum. */
    readonly isPromptNearLimit = computed(() => (this.userPrompt()?.length ?? 0) >= MAX_USER_PROMPT_LENGTH * PROMPT_LENGTH_WARNING_THRESHOLD);

    /** Whether any AI operation (refinement, generation, or rewriting) is active. */
    readonly isAiApplying: Signal<boolean>;

    /** Whether the diff view is open. */
    readonly showDiff = signal(false);

    /** Whether side-by-side diff layout is allowed. */
    readonly allowSplitView = signal(true);

    /** Number of added lines in the current diff. */
    readonly addedLineCount = signal(0);

    /** Number of removed lines in the current diff. */
    readonly removedLineCount = signal(0);

    /** The loaded template problem statement for comparison. */
    readonly templateProblemStatement = signal('');

    /** Whether the template has been loaded. */
    readonly templateLoaded = signal(false);

    /** Tracks the current problem statement content for shouldShowGenerateButton. */
    readonly currentProblemStatement = signal('');

    /** Whether the Hyperion module feature is active. */
    readonly hyperionEnabled: boolean;

    /** Whether to show the "Generate" button (true) or the "Refine" button (false). */
    readonly shouldShowGenerateButton: Signal<boolean>;

    private currentAiOperationSubscription: Subscription | undefined;
    private refinementRequestId = 0;
    private changeHandler?: ProblemStatementChangeHandler;

    constructor(
        private readonly problemStatementService: ProblemStatementService,
        private readonly alertService: AlertService,
        private readonly artemisIntelligenceService: ArtemisIntelligenceService,
        profileService: ProfileService,
        private readonly destroyRef: DestroyRef,
        private readonly injector: Injector,
    ) {
        this.hyperionEnabled = profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);
        this.isAiApplying = computed(() => this.isGeneratingOrRefining() || this.artemisIntelligenceService.isLoading());
        this.shouldShowGenerateButton = computed(() => isTemplateOrEmpty(this.currentProblemStatement(), this.templateProblemStatement(), this.templateLoaded()));
    }

    /** Sets the callback that receives content changes from AI operations. */
    setChangeHandler(handler: ProblemStatementChangeHandler): void {
        this.changeHandler = handler;
    }

    /**
     * Loads the template problem statement for the given exercise.
     * Call this during component initialization (ngOnInit / loadExercise).
     */
    loadTemplate(exercise: ProgrammingExercise | undefined): void {
        this.problemStatementService
            .loadTemplate(exercise)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((result) => {
                this.templateProblemStatement.set(result.template);
                this.templateLoaded.set(result.loaded);
            });
    }

    /**
     * Generates a draft problem statement using the current user prompt.
     *
     * @param exercise The programming exercise.
     * @param editableInstructions Reference to the editable instruction editor.
     */
    generateProblemStatement(exercise: ProgrammingExercise | undefined, editableInstructions: ProgrammingExerciseEditableInstructionComponent | undefined): void {
        if (!exercise) {
            return;
        }
        const prompt = this.userPrompt();
        if (!prompt?.trim()) {
            return;
        }

        this.currentAiOperationSubscription?.unsubscribe();
        this.currentAiOperationSubscription = this.problemStatementService
            .generateProblemStatement(exercise, prompt, (v) => this.isGeneratingOrRefining.set(v))
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (result) => {
                    if (result.success && result.content) {
                        const draftContent = result.content;

                        editableInstructions?.setText(draftContent);
                        exercise.problemStatement = draftContent;
                        this.currentProblemStatement.set(draftContent);
                        this.userPrompt.set('');
                        this.changeHandler?.onContentChanged(draftContent, exercise);
                    } else if (!result.errorHandled) {
                        this.alertService.error('artemisApp.programmingExercise.problemStatement.generationError');
                    }
                },
            });
    }

    /**
     * Refines an existing problem statement globally using the current user prompt.
     * Opens the diff view to show what changed.
     *
     * @param exercise The programming exercise.
     * @param editableInstructions Reference to the editable instruction editor.
     */
    refineProblemStatement(exercise: ProgrammingExercise | undefined, editableInstructions: ProgrammingExerciseEditableInstructionComponent | undefined): void {
        const currentContent = editableInstructions?.getCurrentContent() ?? exercise?.problemStatement;
        const prompt = this.userPrompt();

        if (!exercise || !prompt?.trim()) {
            return;
        }

        if (!currentContent?.trim()) {
            this.alertService.error('artemisApp.programmingExercise.problemStatement.cannotRefineEmpty');
            return;
        }

        this.currentAiOperationSubscription?.unsubscribe();
        this.currentAiOperationSubscription = this.problemStatementService
            .refineGlobally(exercise, currentContent, prompt, (v) => this.isGeneratingOrRefining.set(v))
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (result) => {
                    if (result.success && result.content) {
                        this.showDiff.set(true);
                        this.userPrompt.set('');
                        const expectedContent = result.content;
                        const requestId = ++this.refinementRequestId;
                        afterNextRender(
                            () => {
                                if (requestId === this.refinementRequestId && this.showDiff()) {
                                    editableInstructions?.applyRefinedContent(expectedContent);
                                }
                            },
                            { injector: this.injector },
                        );
                    } else if (!result.errorHandled) {
                        this.alertService.error('artemisApp.programmingExercise.problemStatement.refinementError');
                    }
                },
            });
    }

    /**
     * Handles an inline refinement request from editor selection.
     * Calls the Hyperion API with the selected text and instruction, then applies changes in diff view.
     *
     * @param exercise The programming exercise.
     * @param editableInstructions Reference to the editable instruction editor.
     * @param event The inline refinement event with selection coordinates and instruction.
     */
    onInlineRefinement(
        exercise: ProgrammingExercise | undefined,
        editableInstructions: ProgrammingExerciseEditableInstructionComponent | undefined,
        event: InlineRefinementEvent,
    ): void {
        const currentContent = editableInstructions?.getCurrentContent() ?? exercise?.problemStatement;

        if (!currentContent?.trim()) {
            this.alertService.error('artemisApp.programmingExercise.problemStatement.inlineRefinement.emptyStatementError');
            return;
        }

        this.currentAiOperationSubscription?.unsubscribe();
        const requestId = ++this.refinementRequestId;
        this.currentAiOperationSubscription = this.problemStatementService
            .refineTargeted(exercise, currentContent, event, (v) => this.isGeneratingOrRefining.set(v))
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (result) => {
                    if (result.success && result.content) {
                        this.showDiff.set(true);
                        const refinedContent = result.content;
                        afterNextRender(
                            () => {
                                if (requestId === this.refinementRequestId && this.showDiff()) {
                                    editableInstructions?.applyRefinedContent(refinedContent);
                                }
                            },
                            { injector: this.injector },
                        );
                    } else if (!result.errorHandled) {
                        this.alertService.error('artemisApp.programmingExercise.problemStatement.inlineRefinement.error');
                    }
                    this.currentAiOperationSubscription = undefined;
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.problemStatement.inlineRefinement.error');
                },
            });
    }

    /**
     * Dispatches to either generation or global refinement based on the current state.
     *
     * @param exercise The programming exercise.
     * @param editableInstructions Reference to the editable instruction editor.
     */
    handleProblemStatementAction(exercise: ProgrammingExercise | undefined, editableInstructions: ProgrammingExerciseEditableInstructionComponent | undefined): void {
        if (this.shouldShowGenerateButton()) {
            this.generateProblemStatement(exercise, editableInstructions);
        } else {
            this.refineProblemStatement(exercise, editableInstructions);
        }
    }

    /**
     * Cancels the ongoing AI operation.
     * Preserves the user's prompt so they can retry or modify it.
     */
    cancelAiOperation(): void {
        this.currentAiOperationSubscription?.unsubscribe();
        this.currentAiOperationSubscription = undefined;
        this.isGeneratingOrRefining.set(false);
    }

    /**
     * Closes the diff view. Syncs the current editor content to the exercise model.
     *
     * @param exercise The programming exercise.
     * @param editableInstructions Reference to the editable instruction editor.
     */
    closeDiffView(exercise: ProgrammingExercise | undefined, editableInstructions: ProgrammingExerciseEditableInstructionComponent | undefined): void {
        const currentContent = editableInstructions?.getCurrentContent();
        if (exercise && currentContent != null) {
            exercise.problemStatement = currentContent;
            this.currentProblemStatement.set(currentContent);
            this.changeHandler?.onContentChanged(currentContent, exercise);
        }
        this.showDiff.set(false);
    }

    /**
     * Reverts all changes and closes the diff view.
     *
     * @param exercise The programming exercise.
     * @param editableInstructions Reference to the editable instruction editor.
     */
    revertAllChanges(exercise: ProgrammingExercise | undefined, editableInstructions: ProgrammingExerciseEditableInstructionComponent | undefined): void {
        editableInstructions?.revertAll();
        this.closeDiffView(exercise, editableInstructions);
    }

    /**
     * Updates diff line count signals from the editable instructions diff output.
     */
    onDiffLineChange(event: { ready: boolean; lineChange: LineChange }): void {
        this.addedLineCount.set(event.lineChange.addedLineCount);
        this.removedLineCount.set(event.lineChange.removedLineCount);
    }

    /**
     * Cleans up subscriptions. Call from the host component's ngOnDestroy.
     */
    destroy(): void {
        this.currentAiOperationSubscription?.unsubscribe();
        this.currentAiOperationSubscription = undefined;
    }
}
