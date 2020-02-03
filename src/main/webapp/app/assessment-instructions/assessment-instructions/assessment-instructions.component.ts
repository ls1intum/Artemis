import { Component, Input } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { Exercise, ExerciseType } from 'app/entities/exercise/exercise.model';
import { TextExercise } from 'app/entities/text-exercise/text-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise/modeling-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise/file-upload-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise/programming-exercise.model';

@Component({
    selector: 'jhi-assessment-instructions',
    templateUrl: './assessment-instructions.component.html',
})
export class AssessmentInstructionsComponent {
    exercise: Exercise;
    programmingExercise?: ProgrammingExercise;
    problemStatement: SafeHtml;
    gradingInstructions: SafeHtml;
    sampleSolution?: SafeHtml;
    sampleSolutionModel?: UMLModel;
    sampleSolutionDiagramType?: UMLDiagramType;

    readonly ExerciseType = ExerciseType;

    constructor(private markdownService: ArtemisMarkdown) {}

    @Input('exercise') set exerciseInput(exercise: Exercise) {
        this.exercise = exercise;
        this.problemStatement = this.markdownService.safeHtmlForMarkdown(exercise.problemStatement);
        this.gradingInstructions = this.markdownService.safeHtmlForMarkdown(exercise.gradingInstructions);

        let sampleSolutionMarkdown: string | undefined;
        switch (exercise.type) {
            case ExerciseType.MODELING:
                const modelingExercise = exercise as ModelingExercise;
                sampleSolutionMarkdown = modelingExercise.sampleSolutionExplanation;
                if (modelingExercise.sampleSolutionModel) {
                    this.sampleSolutionModel = JSON.parse(modelingExercise.sampleSolutionModel);
                }
                this.sampleSolutionDiagramType = modelingExercise.diagramType;
                break;
            case ExerciseType.TEXT:
                const textExercise = exercise as TextExercise;
                sampleSolutionMarkdown = textExercise.sampleSolution;
                break;
            case ExerciseType.FILE_UPLOAD:
                const fileUploadExercise = exercise as FileUploadExercise;
                sampleSolutionMarkdown = fileUploadExercise.sampleSolution;
                break;
            case ExerciseType.PROGRAMMING:
                this.programmingExercise = exercise as ProgrammingExercise;
        }
        if (sampleSolutionMarkdown) {
            this.sampleSolution = this.markdownService.safeHtmlForMarkdown(sampleSolutionMarkdown);
        }
    }
}
