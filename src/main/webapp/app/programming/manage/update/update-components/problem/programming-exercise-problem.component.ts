import { AfterViewChecked, Component, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, computed, inject, input, output, signal } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
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
import { finalize } from 'rxjs';
import { Subscription } from 'rxjs';
import { facArtemisIntelligence } from 'app/shared/icons/icons';
import { faBan, faSave, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { MODULE_FEATURE_HYPERION } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FileService } from 'app/shared/service/file.service';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';

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
        MonacoDiffEditorComponent,
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

    /**
     * Handles problem statement changes from the editor
     */
    onProblemStatementChange(newProblemStatement: string): void {
        this.currentProblemStatement.set(newProblemStatement);
        this.problemStatementChange.emit(newProblemStatement);
    }
    programmingExerciseChange = output<ProgrammingExercise>();
    // Problem statement generation properties
    userPrompt = '';
    isGenerating = false;
    isRefining = false;
    private currentGenerationSubscription: Subscription | undefined = undefined;

    // Diff mode properties
    showDiff = false;
    originalProblemStatement = '';
    refinedProblemStatement = '';
    private diffContentSet = false;
    @ViewChild('diffEditor') diffEditor?: MonacoDiffEditorComponent;
    private templateProblemStatement = signal<string>('');
    private currentProblemStatement = signal<string>('');

    // icons
    facArtemisIntelligence = facArtemisIntelligence;
    faSpinner = faSpinner;
    faBan = faBan;
    faSave = faSave;

    // Injected services
    private hyperionApiService = inject(HyperionProblemStatementApiService);
    private translateService = inject(TranslateService);
    private alertService = inject(AlertService);
    private profileService = inject(ProfileService);
    private fileService = inject(FileService);

    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);

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
    }

    /**
     * Cancels the ongoing problem statement generation or refinement.
     * Preserves the user's prompt so they can retry or modify it.
     */
    cancelGeneration(): void {
        if (this.currentGenerationSubscription) {
            this.currentGenerationSubscription.unsubscribe();
            this.currentGenerationSubscription = undefined;
        }
        this.isGenerating = false;
        this.isRefining = false;
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
            // Set diff editor content after view update
            setTimeout(() => {
                if (this.diffEditor) {
                    this.diffEditor.setFileContents(this.originalProblemStatement, this.refinedProblemStatement, 'original.md', 'refined.md');
                }
            }, 0);
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
}
