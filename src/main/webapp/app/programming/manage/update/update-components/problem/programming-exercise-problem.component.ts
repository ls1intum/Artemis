import { Component, EventEmitter, Input, Output, inject, input, output, viewChild } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
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
import { Popover, PopoverModule } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ProblemStatementGenerationRequest } from 'app/openapi/model/problemStatementGenerationRequest';
import { finalize } from 'rxjs';

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
    ],
})
export class ProgrammingExerciseProblemComponent {
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

    generatePopover = viewChild<Popover>('generatePopover');

    // Problem statement generation properties
    userPrompt = '';
    isGenerating = false;

    // Injected services
    private hyperionApiService = inject(HyperionProblemStatementApiService);

    /**
     * Opens the problem statement generation popover
     */
    openGeneratePopover(event: Event): void {
        this.userPrompt = ''; // Reset the prompt
        this.generatePopover()?.show(event);
    }
    /**
     * Cancels the problem statement generation and closes the popover
     */
    cancelGeneration(): void {
        this.userPrompt = '';
        this.generatePopover()?.hide();
    }
    /**
     * Generates a draft problem statement using the user's prompt
     */
    generateProblemStatement(): void {
        const exercise = this.programmingExercise();
        if (!this.userPrompt?.trim() || !exercise?.course?.id) {
            return;
        }

        this.isGenerating = true;

        const request: ProblemStatementGenerationRequest = {
            userPrompt: this.userPrompt.trim(),
        };

        this.hyperionApiService
            .generateProblemStatement(exercise.course.id, request)
            .pipe(
                finalize(() => {
                    this.isGenerating = false;
                }),
            )
            .subscribe({
                next: (response) => {
                    if (response.draftProblemStatement && exercise) {
                        // Update the programming exercise problem statement
                        exercise.problemStatement = response.draftProblemStatement;
                        this.programmingExerciseChange.emit(exercise);
                    }

                    // Close the popover
                    this.generatePopover()?.hide();
                    this.userPrompt = '';
                },
                error: (error) => {
                    // eslint-disable-next-line no-undef
                    console.error('Error generating problem statement:', error);
                },
            });
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
