import { Component, Injector, OnDestroy, OnInit, afterNextRender, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { faBan, faSave, faSpinner, faTableColumns } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExerciseCreationConfig } from 'app/programming/manage/update/programming-exercise-creation-config';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbAlert, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { PopoverModule } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { Subscription } from 'rxjs';
import { ProblemStatementService } from 'app/programming/manage/services/problem-statement.service';
import { InlineRefinementEvent, isTemplateOrEmpty } from 'app/programming/manage/shared/problem-statement.utils';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { TranslateService } from '@ngx-translate/core';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ButtonComponent, ButtonSize, ButtonType, TooltipPlacement } from 'app/shared/components/buttons/button/button.component';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { LineChange } from 'app/programming/shared/utils/diff.utils';

@Component({
    selector: 'jhi-programming-exercise-problem',
    templateUrl: './programming-exercise-problem.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [
        CommonModule,
        TranslateDirective,
        NgbAlert,
        NgbTooltip,

        ProgrammingExerciseInstructionComponent,
        ProgrammingExerciseEditableInstructionComponent,
        CompetencySelectionComponent,
        FormsModule,
        ArtemisTranslatePipe,
        PopoverModule,
        ButtonModule,
        FaIconComponent,
        HelpIconComponent,
        ButtonComponent,
        GitDiffLineStatComponent,
    ],
})
export class ProgrammingExerciseProblemComponent implements OnInit, OnDestroy {
    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;

    programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();
    programmingExercise = input<ProgrammingExercise>();
    problemStatementChange = output<string>();
    programmingExerciseChange = output<ProgrammingExercise>();

    userPrompt = signal('');
    isGeneratingOrRefining = signal(false);
    private currentGenerationSubscription: Subscription | undefined = undefined;
    private profileService = inject(ProfileService);
    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);

    facArtemisIntelligence = facArtemisIntelligence;
    faSpinner = faSpinner;

    faBan = faBan;
    faSave = faSave;
    faTableColumns = faTableColumns;

    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;
    readonly TooltipPlacement = TooltipPlacement;

    private translateService = inject(TranslateService);
    private problemStatementService = inject(ProblemStatementService);
    private alertService = inject(AlertService);
    private injector = inject(Injector);

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
        this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
        if (exercise) {
            exercise.problemStatement = newProblemStatement;
            this.programmingExerciseChange.emit(exercise);
        }
        this.problemStatementChange.emit(newProblemStatement);
    }

    showDiff = signal(false);
    allowSplitView = signal(true);
    addedLineCount = signal(0);
    removedLineCount = signal(0);

    private templateProblemStatement = signal<string>('');
    private templateLoaded = signal<boolean>(false);
    private currentProblemStatement = signal<string>('');

    readonly editableInstructions = viewChild<ProgrammingExerciseEditableInstructionComponent>('editableInstructions');

    private artemisIntelligenceService = inject(ArtemisIntelligenceService);

    /** True if any AI operation is in progress (refinement, generation or rewriting) */
    protected isAiApplying = computed(() => this.isGeneratingOrRefining() || this.artemisIntelligenceService.isLoading());

    /**
     * Lifecycle hook to capture the initial problem statement for comparison.
     * Also loads the template problem statement for robust comparison.
     */
    ngOnInit(): void {
        const exercise = this.programmingExercise();

        if (exercise?.problemStatement) {
            this.currentProblemStatement.set(exercise.problemStatement);
        }

        // Use shared service to load template
        this.problemStatementService.loadTemplate(exercise, this.templateProblemStatement, this.templateLoaded);
    }

    /**
     * Cancels the ongoing problem statement generation, refinement.
     * Preserves the user's prompt so they can retry or modify it.
     * Resets all in-progress states.
     */
    cancelGeneration(): void {
        if (this.currentGenerationSubscription) {
            this.currentGenerationSubscription.unsubscribe();
            this.currentGenerationSubscription = undefined;
        }

        this.isGeneratingOrRefining.set(false);
    }

    /**
     * Computed signal that determines whether to show the generate or refine button.
     * Reacts to changes in the problem statement.
     */
    shouldShowGenerateButton = computed(() => isTemplateOrEmpty(this.currentProblemStatement(), this.templateProblemStatement(), this.templateLoaded()));

    /**
     * Generates a draft problem statement using the user's prompt
     */
    generateProblemStatement(): void {
        const exercise = this.programmingExercise();
        if (!exercise) {
            return;
        }
        const prompt = this.userPrompt();

        if (!prompt?.trim()) {
            return;
        }

        this.currentGenerationSubscription = this.problemStatementService.generateProblemStatement(exercise, prompt, this.isGeneratingOrRefining).subscribe({
            next: (result) => {
                if (result.success && result.content && exercise) {
                    const draftContent = result.content;
                    const editorComponent = this.editableInstructions();

                    // Update the editor using the facade method
                    if (editorComponent) {
                        editorComponent.setText(draftContent);
                    }

                    // Always update the model/state
                    exercise.problemStatement = draftContent;
                    this.currentProblemStatement.set(draftContent);
                    this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
                    this.problemStatementChange.emit(draftContent);
                    this.programmingExerciseChange.emit(exercise);

                    // If editor was unavailable, schedule a retry when ready
                    if (!editorComponent) {
                        afterNextRender(
                            () => {
                                this.editableInstructions()?.setText(draftContent);
                            },
                            { injector: this.injector },
                        );
                    }
                }
                this.userPrompt.set('');
                this.currentGenerationSubscription = undefined;
            },
            error: () => {
                this.alertService.error('artemisApp.programmingExercise.problemStatement.generationFailed');
                this.userPrompt.set('');
                this.currentGenerationSubscription = undefined;
            },
        });
    }

    /**
     * Refines an existing problem statement using the user's prompt.
     * Changes are applied directly to the editor and sync immediately.
     * Opens diff view to show what changed (left = snapshot, right = live).
     */
    refineProblemStatement(): void {
        const exercise = this.programmingExercise();
        const currentContent = this.editableInstructions()?.getCurrentContent() ?? exercise?.problemStatement;
        const prompt = this.userPrompt();

        if (!exercise || !prompt?.trim() || !currentContent?.trim()) {
            return;
        }

        this.currentGenerationSubscription = this.problemStatementService.refineGlobally(exercise, currentContent, prompt, this.isGeneratingOrRefining).subscribe({
            next: (result) => {
                if (result.success && result.content) {
                    this.showDiff.set(true);
                    const refinedContent = result.content;
                    afterNextRender(
                        () => {
                            this.editableInstructions()?.applyRefinedContent(refinedContent);
                        },
                        { injector: this.injector },
                    );

                    this.userPrompt.set('');
                }
                this.currentGenerationSubscription = undefined;
            },
            error: () => {
                this.alertService.error('artemisApp.programmingExercise.problemStatement.refinementFailed');
                this.userPrompt.set('');
                this.currentGenerationSubscription = undefined;
            },
        });
    }

    /**
     * Closes the diff view. In live-synced mode, changes are already applied.
     * The user can use Monaco's inline revert buttons to undo specific chunks.
     */
    closeDiffView(): void {
        const exercise = this.programmingExercise();
        // Get the current content from the editor (already synced)
        const currentContent = this.editableInstructions()?.getCurrentContent();
        if (exercise && currentContent != null) {
            exercise.problemStatement = currentContent;
            this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
            this.problemStatementChange.emit(currentContent);
            this.programmingExerciseChange.emit(exercise);
            this.currentProblemStatement.set(currentContent);
        }
        this.showDiff.set(false);
    }

    /**
     * Reverts all changes and closes the diff view.
     * Restores the content to the state before refinement was applied.
     */
    revertAllChanges(): void {
        this.editableInstructions()?.revertAll();
        this.closeDiffView();
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

    /**
     * Get the translated example placeholder text for the input field
     */
    getTranslatedPlaceholder(): string {
        return this.translateService.instant('artemisApp.programmingExercise.problemStatement.examplePlaceholder');
    }

    /**
     * Handles changes to competency links
     */
    onCompetencyLinksChange(competencyLinks: ProgrammingExercise['competencyLinks']): void {
        const exercise = this.programmingExercise();
        if (exercise) {
            exercise.competencyLinks = competencyLinks;
            this.programmingExerciseChange.emit(exercise);
        }
    }

    /**
     * Handles inline refinement request from editor selection.
     * Calls the Hyperion API with the selected text and instruction, then applies changes directly.
     */
    onInlineRefinement(event: InlineRefinementEvent): void {
        const exercise = this.programmingExercise();
        const currentContent = this.editableInstructions()?.getCurrentContent() ?? exercise?.problemStatement;

        if (!currentContent?.trim()) {
            return;
        }

        this.currentGenerationSubscription = this.problemStatementService.refineTargeted(exercise, currentContent, event, this.isGeneratingOrRefining).subscribe({
            next: (result) => {
                if (result.success && result.content) {
                    this.showDiff.set(true);
                    const refinedContent = result.content;
                    afterNextRender(
                        () => {
                            this.editableInstructions()?.applyRefinedContent(refinedContent);
                        },
                        { injector: this.injector },
                    );
                }
                this.currentGenerationSubscription = undefined;
            },
        });
    }

    onDiffLineChange(event: { ready: boolean; lineChange: LineChange }): void {
        this.addedLineCount.set(event.lineChange.addedLineCount);
        this.removedLineCount.set(event.lineChange.removedLineCount);
    }
}
