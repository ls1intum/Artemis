import { Component, OnDestroy, OnInit, computed, inject, input, output, signal, viewChild } from '@angular/core';
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
    ],
})
export class ProgrammingExerciseProblemComponent implements OnInit, OnDestroy {
    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly ProjectType = ProjectType;
    protected readonly AssessmentType = AssessmentType;
    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    protected readonly faQuestionCircle = faQuestionCircle;

    programmingExerciseCreationConfig = input.required<ProgrammingExerciseCreationConfig>();
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();
    programmingExercise = input<ProgrammingExercise>();
    problemStatementChange = output<string>();
    programmingExerciseChange = output<ProgrammingExercise>();
    // Problem statement generation properties
    userPrompt = '';
    isGenerating = signal(false);
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
        this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
        if (exercise) {
            exercise.problemStatement = newProblemStatement;
            this.programmingExerciseChange.emit(exercise);
        }
        this.problemStatementChange.emit(newProblemStatement);
    }

    isRefining = signal(false);

    // Diff mode properties
    showDiff = signal(false);
    originalProblemStatement = signal('');
    refinedProblemStatement = signal('');
    private templateProblemStatement = signal<string>('');
    private currentProblemStatement = signal<string>('');

    // Injected services
    private fileService = inject(FileService);

    // Template references
    readonly editableInstructions = viewChild<ProgrammingExerciseEditableInstructionComponent>('editableInstructions');

    // Inline comment state
    /** True if any AI operation is in progress (refinement or generation) */
    protected isAnyApplying = computed(() => this.isRefining() || this.isGenerating());

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
                error: () => {
                    // Clear template on error so shouldShowGenerateButton falls back to checking empty content
                    this.templateProblemStatement.set('');
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

        // Reset global generation/refinement flags
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
                    // Check if the response contains an empty or invalid problem statement
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
     * Refines an existing problem statement using the user's prompt
     */
    refineProblemStatement(): void {
        const exercise = this.programmingExercise();
        const courseId = exercise?.course?.id ?? exercise?.exerciseGroup?.exam?.course?.id;
        if (!this.userPrompt?.trim() || !courseId || !exercise?.problemStatement) {
            return;
        }

        this.isRefining.set(true);

        const request: ProblemStatementRefinementRequest = {
            problemStatementText: exercise.problemStatement,
            userPrompt: this.userPrompt.trim(),
        };

        this.currentGenerationSubscription = this.hyperionApiService
            .refineProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.isRefining.set(false);
                    this.currentGenerationSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    // Check if refinement was successful
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        // Store original and refined content for diff view
                        this.originalProblemStatement.set(exercise.problemStatement || '');
                        this.refinedProblemStatement.set(response.refinedProblemStatement);
                        this.showDiff.set(true);
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
     * Accepts the refined problem statement and applies the changes
     */
    acceptRefinement(): void {
        const exercise = this.programmingExercise();
        const refined = this.refinedProblemStatement();
        if (exercise && refined) {
            exercise.problemStatement = refined;
            this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
            this.problemStatementChange.emit(refined);
            this.programmingExerciseChange.emit(exercise);
            this.currentProblemStatement.set(refined);
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
        this.showDiff.set(false);
        this.originalProblemStatement.set('');
        this.refinedProblemStatement.set('');
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

    /**
     * Handles inline refinement request from editor selection.
     * Calls the Hyperion API with the selected text and instruction, then shows diff.
     */
    onInlineRefinement(event: { instruction: string; startLine: number; endLine: number; startColumn: number; endColumn: number }): void {
        const exercise = this.programmingExercise();
        const courseId = exercise?.course?.id ?? exercise?.exerciseGroup?.exam?.course?.id;

        if (!courseId || !exercise?.problemStatement?.trim()) {
            this.alertService.error('artemisApp.programmingExercise.inlineRefine.error');
            return;
        }

        this.isRefining.set(true);

        const apiComment: ApiInlineComment = {
            startLine: event.startLine,
            endLine: event.endLine,
            startColumn: event.startColumn,
            endColumn: event.endColumn,
            instruction: event.instruction,
        };

        const request: ProblemStatementRefinementRequest = {
            problemStatementText: exercise.problemStatement,
            inlineComments: [apiComment],
        };

        this.currentGenerationSubscription = this.hyperionApiService
            .refineProblemStatement(courseId, request)
            .pipe(
                finalize(() => {
                    this.isRefining.set(false);
                    this.currentGenerationSubscription = undefined;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (response.refinedProblemStatement && response.refinedProblemStatement.trim() !== '') {
                        // Store original and refined content for diff view
                        this.originalProblemStatement.set(exercise.problemStatement || '');
                        this.refinedProblemStatement.set(response.refinedProblemStatement);
                        this.showDiff.set(true);
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
}
