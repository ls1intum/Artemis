import { Component, Input, inject, input, model } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { UMLDiagramType, UMLModel, importDiagram } from '@tumaet/apollon';
import { SecureLinkDirective } from 'app/assessment/manage/secure-link.directive';
import { StructuredGradingInstructionsAssessmentLayoutComponent } from 'app/assessment/manage/structured-grading-instructions-assessment-layout/structured-grading-instructions-assessment-layout.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExpandableSectionComponent } from '../expandable-section/expandable-section.component';

@Component({
    selector: 'jhi-assessment-instructions',
    templateUrl: './assessment-instructions.component.html',
    imports: [
        ExpandableSectionComponent,
        StructuredGradingInstructionsAssessmentLayoutComponent,
        ProgrammingExerciseInstructionComponent,
        SecureLinkDirective,
        ButtonComponent,
        TranslateDirective,
        ModelingEditorComponent,
    ],
})
export class AssessmentInstructionsComponent {
    private markdownService = inject(ArtemisMarkdownService);

    exercise: Exercise;
    programmingExercise?: ProgrammingExercise;
    problemStatement: SafeHtml;
    gradingInstructions?: SafeHtml;
    sampleSolutionExplanation?: SafeHtml;
    sampleSolutionModel?: UMLModel;
    sampleSolutionDiagramType?: UMLDiagramType;

    readonly isAssessmentTraining = input(false);
    readonly showAssessmentInstructions = input(true);

    readonly readOnly = input<boolean>();
    // For programming exercises we hand over the participation or use the template participation
    readonly programmingParticipation = input<ProgrammingExerciseStudentParticipation>();
    readonly gradingCriteria = model<GradingCriterion[]>();

    readonly ExerciseType = ExerciseType;

    // eslint-disable-next-line @angular-eslint/no-input-rename
    @Input('exercise') set exerciseInput(exercise: Exercise) {
        this.exercise = exercise;
        this.problemStatement = this.markdownService.safeHtmlForMarkdown(exercise.problemStatement);
        if (exercise.gradingInstructions) {
            this.gradingInstructions = this.markdownService.safeHtmlForMarkdown(exercise.gradingInstructions);
        }
        this.gradingCriteria.set(exercise.gradingCriteria);

        let sampleSolutionMarkdown: string | undefined;
        switch (exercise.type) {
            case ExerciseType.MODELING:
                const modelingExercise = exercise as ModelingExercise;
                sampleSolutionMarkdown = modelingExercise.exampleSolutionExplanation;
                if (modelingExercise.exampleSolutionModel) {
                    this.sampleSolutionModel = importDiagram(JSON.parse(modelingExercise.exampleSolutionModel));
                }
                this.sampleSolutionDiagramType = modelingExercise.diagramType;
                break;
            case ExerciseType.TEXT:
                const textExercise = exercise as TextExercise;
                sampleSolutionMarkdown = textExercise.exampleSolution;
                break;
            case ExerciseType.FILE_UPLOAD:
                const fileUploadExercise = exercise as FileUploadExercise;
                sampleSolutionMarkdown = fileUploadExercise.exampleSolution;
                break;
            case ExerciseType.PROGRAMMING:
                this.programmingExercise = exercise as ProgrammingExercise;
        }
        if (sampleSolutionMarkdown) {
            this.sampleSolutionExplanation = this.markdownService.safeHtmlForMarkdown(sampleSolutionMarkdown);
        }
    }
}
