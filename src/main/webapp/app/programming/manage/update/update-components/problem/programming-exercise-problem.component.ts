import { Component, OnDestroy, inject, input, output, viewChild } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { CompetencyExerciseLink, CompetencyLearningObjectLink, MEDIUM_COMPETENCY_LINK_WEIGHT } from 'app/atlas/shared/entities/competency.model';
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
import { ChecklistPanelComponent } from './checklist-panel/checklist-panel.component';

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
        ChecklistPanelComponent,
    ],
})
export class ProgrammingExerciseProblemComponent implements OnDestroy {
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
    isGenerating = false;
    private currentGenerationSubscription: Subscription | undefined = undefined;
    private profileService = inject(ProfileService);
    hyperionEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_HYPERION);

    // Child component reference for refreshing competency selection
    private competencySelectionComponent = viewChild(CompetencySelectionComponent);

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
     * Cancels the ongoing problem statement generation.
     * Preserves the user's prompt so they can retry or modify it.
     */
    cancelGeneration(): void {
        if (this.currentGenerationSubscription) {
            this.currentGenerationSubscription.unsubscribe();
            this.currentGenerationSubscription = undefined;
        }
        this.isGenerating = false;
    }

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
                        this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
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

    protected readonly ButtonSize = ButtonSize;

    /**
     * Get the translated example placeholder text for the input field
     */
    getTranslatedPlaceholder(): string {
        return this.translateService.instant('artemisApp.programmingExercise.problemStatement.examplePlaceholder');
    }

    /**
     * Handles changes to competency links from either the checklist panel or the competency selection.
     * Also refreshes the CompetencySelectionComponent to reflect changes (e.g., newly created/linked competencies).
     */
    onCompetencyLinksChange(competencyLinks: CompetencyExerciseLink[] | CompetencyLearningObjectLink[] | undefined): void {
        if (!competencyLinks) return;
        const exercise = this.programmingExercise();
        if (exercise) {
            exercise.competencyLinks = competencyLinks as CompetencyExerciseLink[];
            this.programmingExerciseChange.emit(exercise);
        }
        this.refreshCompetencySelection(competencyLinks);
    }

    /**
     * Updates the CompetencySelectionComponent's available list, selection state, and checkbox states
     * so that newly linked or created competencies are immediately visible.
     *
     * TODO: This method directly accesses CompetencySelectionComponent internals (competencyLinks,
     * checkboxStates, writeValue). Ideally, expose a public refresh() method on
     * CompetencySelectionComponent or use input bindings to trigger the update declaratively.
     */
    private refreshCompetencySelection(competencyLinks: CompetencyExerciseLink[] | CompetencyLearningObjectLink[]): void {
        const selection = this.competencySelectionComponent();
        if (!selection || !competencyLinks) return;

        // Add any new competencies to the available list that aren't there yet
        if (selection.competencyLinks) {
            const availableIds = new Set(selection.competencyLinks.map((l) => l.competency?.id).filter(Boolean));
            for (const link of competencyLinks) {
                if (link.competency?.id && !availableIds.has(link.competency.id)) {
                    selection.competencyLinks.push(new CompetencyLearningObjectLink(link.competency, link.weight ?? MEDIUM_COMPETENCY_LINK_WEIGHT));
                }
            }
        }

        // Update selection state via writeValue
        selection.writeValue(competencyLinks);

        // Update checkbox states to match the current selection
        if (selection.competencyLinks) {
            const selectedIds = new Set(competencyLinks.map((l) => l.competency?.id).filter(Boolean));
            selection.checkboxStates = selection.competencyLinks.reduce(
                (states, cl) => {
                    if (cl.competency?.id) {
                        states[cl.competency.id] = selectedIds.has(cl.competency.id);
                    }
                    return states;
                },
                {} as Record<number, boolean>,
            );
        }
    }

    onDifficultyChange(difficulty: string): void {
        const exercise = this.programmingExercise();
        if (exercise) {
            exercise.difficulty = DifficultyLevel[difficulty as keyof typeof DifficultyLevel];
            this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
            this.programmingExerciseChange.emit(exercise);
        }
    }

    onInstructionChange(problemStatement: string): void {
        const exercise = this.programmingExercise();
        this.programmingExerciseCreationConfig().hasUnsavedChanges = true;
        if (exercise) {
            exercise.problemStatement = problemStatement;
            this.programmingExerciseChange.emit(exercise);
        }
        this.problemStatementChange.emit(problemStatement);
    }
}
