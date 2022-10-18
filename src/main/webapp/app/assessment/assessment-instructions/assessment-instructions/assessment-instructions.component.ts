import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

@Component({
    selector: 'jhi-assessment-instructions',
    templateUrl: './assessment-instructions.component.html',
})
export class AssessmentInstructionsComponent {
    exercise: Exercise;
    programmingExercise?: ProgrammingExercise;
    problemStatement: SafeHtml;
    gradingInstructions: SafeHtml;
    sampleSolutionExplanation?: SafeHtml;
    sampleSolutionModel?: UMLModel;
    sampleSolutionDiagramType?: UMLDiagramType;
    criteria: GradingCriterion[];

    @Input() isAssessmentTraining = false;
    @Input() showAssessmentInstructions = true;

    @Input() readOnly: boolean;
    // For programming exercises we hand over the participation or use the template participation
    @Input() programmingParticipation?: ProgrammingExerciseStudentParticipation;

    readonly ExerciseType = ExerciseType;

    // extension points, see shared/extension-point
    @ContentChild('overrideTitle') overrideTitle: TemplateRef<any>;

    constructor(private markdownService: ArtemisMarkdownService) {}

    @Input('exercise') set exerciseInput(exercise: Exercise) {
        this.exercise = exercise;
        this.problemStatement = this.markdownService.safeHtmlForMarkdown(exercise.problemStatement);
        this.gradingInstructions = this.markdownService.safeHtmlForMarkdown(exercise.gradingInstructions);
        // make sure the array is initialized
        this.criteria = exercise.gradingCriteria || [];

        let sampleSolutionMarkdown: string | undefined;
        switch (exercise.type) {
            case ExerciseType.MODELING:
                const modelingExercise = exercise as ModelingExercise;
                sampleSolutionMarkdown = modelingExercise.exampleSolutionExplanation;
                if (modelingExercise.exampleSolutionModel) {
                    this.sampleSolutionModel = JSON.parse(modelingExercise.exampleSolutionModel);
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
