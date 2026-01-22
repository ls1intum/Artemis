import { Component, OnDestroy, OnInit, computed, inject, input, output, signal, viewChild } from '@angular/core';
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
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ProblemStatementGenerationRequest } from 'app/openapi/model/problemStatementGenerationRequest';
import { ProblemStatementGlobalRefinementRequest } from 'app/openapi/model/problemStatementGlobalRefinementRequest';
import { ProblemStatementTargetedRefinementRequest } from 'app/openapi/model/problemStatementTargetedRefinementRequest';
import { Subscription, finalize } from 'rxjs';
import { facArtemisIntelligence } from 'app/shared/icons/icons';

import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

import { FileService } from 'app/shared/service/file.service';
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

    userPrompt = '';
    isGenerating = signal(false);
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
        this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
        if (exercise) {
            exercise.problemStatement = newProblemStatement;
            this.programmingExerciseChange.emit(exercise);
        }
        this.problemStatementChange.emit(newProblemStatement);
    }

    isRefining = signal(false);

    showDiff = signal(false);
    allowSplitView = signal(true);
    addedLineCount = signal(0);
    removedLineCount = signal(0);

    private templateProblemStatement = signal<string>('');
    private templateLoaded = signal<boolean>(false);
    private currentProblemStatement = signal<string>('');

    private fileService = inject(FileService);

    readonly editableInstructions = viewChild<ProgrammingExerciseEditableInstructionComponent>('editableInstructions');

    private artemisIntelligenceService = inject(ArtemisIntelligenceService);

    /** True if any AI operation is in progress (refinement or generation) */
    protected isAnyApplying = computed(() => this.isRefining() || this.isGenerating() || this.artemisIntelligenceService.isLoading());

    /**
     * Lifecycle hook to capture the initial problem statement for comparison.
     * Also loads the template problem statement for robust comparison.
     */
    ngOnInit(): void {
        const exercise = this.programmingExercise();

        if (exercise?.problemStatement) {
            this.currentProblemStatement.set(exercise.problemStatement);
        }

        if (exercise?.programmingLanguage) {
            this.fileService.getTemplateFile(exercise.programmingLanguage, exercise.projectType).subscribe({
                next: (template) => {
                    this.templateProblemStatement.set(template);
                    this.templateLoaded.set(true);
                },
                error: () => {
                    this.templateProblemStatement.set('');
                    this.templateLoaded.set(false);
                },
            });
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

        this.isGenerating.set(false);
        this.isRefining.set(false);
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

        if (!problemStatement || problemStatement.trim() === '') {
            return true;
        }

        if (!this.templateLoaded()) {
            return true;
        }

        const normalizedProblemStatement = this.normalizeString(problemStatement);
        const normalizedTemplate = this.normalizeString(template);

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

        this.isGenerating.set(true);

        const request: ProblemStatementGenerationRequest = {
            userPrompt: this.userPrompt.trim(),
        };

        this.currentGenerationSubscription = this.hyperionApiService
            .generateProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.isGenerating.set(false);
                    this.currentGenerationSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (!response.draftProblemStatement || response.draftProblemStatement.trim() === '') {
                        this.alertService.error('artemisApp.programmingExercise.problemStatement.generationError');
                        return;
                    }

                    if (response.draftProblemStatement && exercise) {
                        exercise.problemStatement = response.draftProblemStatement;
                        this.currentProblemStatement.set(response.draftProblemStatement);
                        this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
                        this.problemStatementChange.emit(response.draftProblemStatement);
                        this.programmingExerciseChange.emit(exercise);

                        this.editableInstructions()?.markdownEditorMonaco?.monacoEditor?.setText(response.draftProblemStatement);
                    }
                    this.userPrompt = '';

                    // Show success alert
                    this.alertService.success('artemisApp.programmingExercise.problemStatement.generationSuccess');
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.problemStatement.generationError');
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
        const courseId = exercise?.course?.id ?? exercise?.exerciseGroup?.exam?.course?.id;
        const currentContent = this.editableInstructions()?.getCurrentContent() ?? exercise?.problemStatement;

        if (!this.userPrompt?.trim() || !courseId || !currentContent) {
            return;
        }

        this.isRefining.set(true);

        const request: ProblemStatementGlobalRefinementRequest = {
            problemStatementText: currentContent,
            userPrompt: this.userPrompt.trim(),
        };

        this.currentGenerationSubscription = this.hyperionApiService
            .refineProblemStatementGlobally(courseId, request)
            .pipe(
                finalize(() => {
                    this.isRefining.set(false);
                    this.currentGenerationSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        this.showDiff.set(true);
                        const refinedContent = response.refinedProblemStatement;
                        setTimeout(() => {
                            this.editableInstructions()?.applyRefinedContent(refinedContent);
                        }, 50);

                        this.userPrompt = '';
                    } else if (response.originalProblemStatement) {
                        // Refinement failed: keep the original problem statement
                        this.alertService.warning('artemisApp.programmingExercise.problemStatement.refinementFailed');
                    } else {
                        this.alertService.error('artemisApp.programmingExercise.problemStatement.refinementError');
                    }
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.problemStatement.refinementError');
                },
            });
    }

    /**
     * Closes the diff view. In live-synced mode, changes are already applied.
     * The user can use Monaco's inline revert buttons to undo specific hunks.
     */
    /**
     * Closes the diff view. In live-synced mode, changes are already applied.
     * The user can use Monaco's inline revert buttons to undo specific hunks.
     */
    closeDiffView(): void {
        const exercise = this.programmingExercise();
        // Get the current content from the editor (already synced)
        const currentContent = this.editableInstructions()?.getCurrentContent();
        if (exercise && currentContent) {
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
    onCompetencyLinksChange(competencyLinks: any): void {
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
    onInlineRefinement(event: { instruction: string; startLine: number; endLine: number; startColumn: number; endColumn: number }): void {
        const exercise = this.programmingExercise();
        const courseId = exercise?.course?.id ?? exercise?.exerciseGroup?.exam?.course?.id;
        const currentContent = this.editableInstructions()?.getCurrentContent() ?? exercise?.problemStatement;

        if (!courseId || !currentContent?.trim()) {
            this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
            return;
        }

        this.isRefining.set(true);

        const request: ProblemStatementTargetedRefinementRequest = {
            problemStatementText: currentContent,
            startLine: event.startLine,
            endLine: event.endLine,
            startColumn: event.startColumn,
            endColumn: event.endColumn,
            instruction: event.instruction,
        };

        this.currentGenerationSubscription = this.hyperionApiService
            .refineProblemStatementTargeted(courseId, request)
            .pipe(
                finalize(() => {
                    this.isRefining.set(false);
                    this.currentGenerationSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        this.showDiff.set(true);

                        const refinedContent = response.refinedProblemStatement;
                        setTimeout(() => {
                            this.editableInstructions()?.applyRefinedContent(refinedContent);
                        }, 50);

                        this.alertService.success('artemisApp.programmingExercise.inlineRefine.success');
                    } else {
                        this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
                    }
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
                },
            });
    }

    onDiffLineChange(event: { ready: boolean; lineChange: LineChange }): void {
        this.addedLineCount.set(event.lineChange.addedLineCount);
        this.removedLineCount.set(event.lineChange.removedLineCount);
    }
}
