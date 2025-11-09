import { Component, EventEmitter, Input, OnDestroy, Output, inject, input, output } from '@angular/core';
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
export class ProgrammingExerciseProblemComponent implements OnDestroy {
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
    private currentGenerationSubscription: Subscription | null = null;
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
            this.currentGenerationSubscription = null;
        }
    }

    /**
     * Cancels the problem statement generation and closes the popover
     */
    cancelGeneration(): void {
        if (this.currentGenerationSubscription) {
            this.currentGenerationSubscription.unsubscribe();
            this.currentGenerationSubscription = null;
        }
        this.isGenerating = false;
        this.userPrompt = '';
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
                    this.currentGenerationSubscription = null;
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
