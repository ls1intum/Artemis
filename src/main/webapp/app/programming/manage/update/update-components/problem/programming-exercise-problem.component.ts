import { Component, EventEmitter, Input, Output, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { faCheckCircle, faExclamationTriangle, faQuestionCircle, faRobot, faSpinner } from '@fortawesome/free-solid-svg-icons';
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
import { CodeGenerationResultDTO, CodeGenerationService } from 'app/hyperion/code-generation.service';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-programming-exercise-problem',
    templateUrl: './programming-exercise-problem.component.html',
    styleUrls: ['../../../../shared/programming-exercise-form.scss'],
    imports: [
        CommonModule,
        TranslateDirective,
        NgbAlert,
        ProgrammingExerciseInstructionComponent,
        ProgrammingExerciseEditableInstructionComponent,
        CompetencySelectionComponent,
        FormsModule,
        ArtemisTranslatePipe,
        FaIconComponent,
    ],
})
export class ProgrammingExerciseProblemComponent {
    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly ProjectType = ProjectType;
    protected readonly AssessmentType = AssessmentType;
    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faRobot = faRobot;
    protected readonly faSpinner = faSpinner;
    protected readonly faCheckCircle = faCheckCircle;
    protected readonly faExclamationTriangle = faExclamationTriangle;

    private codeGenerationService = inject(CodeGenerationService);
    private alertService = inject(AlertService);

    isGeneratingCode = false;
    generationResult?: CodeGenerationResultDTO;

    @Input({ required: true }) programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
    isEditFieldDisplayedRecord = input.required<Record<ProgrammingExerciseInputField, boolean>>();

    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();
    @Output() problemStatementChange = new EventEmitter<string>();

    programmingExercise: ProgrammingExercise;

    @Input()
    get exercise() {
        return this.programmingExercise;
    }

    set exercise(exercise: ProgrammingExercise) {
        this.programmingExercise = exercise;
        this.exerciseChange.emit(this.programmingExercise);
    }

    /**
     * Generates code for the current programming exercise using AI
     */
    generateCode(): void {
        if (!this.programmingExercise?.id || this.isGeneratingCode) {
            return;
        }

        this.isGeneratingCode = true;
        this.generationResult = undefined;

        this.codeGenerationService.generateCode(this.programmingExercise.id).subscribe({
            next: (response) => {
                this.generationResult = response;
                this.isGeneratingCode = false;

                if (response.success) {
                    this.alertService.addAlert({
                        type: AlertType.SUCCESS,
                        translationKey: 'artemisApp.programmingExercise.codeGeneration.success',
                    });
                } else {
                    this.alertService.addAlert({
                        type: AlertType.WARNING,
                        translationKey: 'artemisApp.programmingExercise.codeGeneration.partialSuccess',
                    });
                }
            },
            error: (error) => {
                this.isGeneratingCode = false;
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    translationKey: 'artemisApp.programmingExercise.codeGeneration.error',
                });
            },
        });
    }
}
