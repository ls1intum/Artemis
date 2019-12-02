import { Component, Input, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { UMLModel, UMLDiagramType } from '@ls1intum/apollon';
import { Exercise, ExerciseType } from 'app/entities/exercise';
import { FileUploadExercise } from 'app/entities/file-upload-exercise';

@Component({
    selector: 'jhi-expandable-sample-solution',
    templateUrl: './expandable-sample-solution.component.html',
    styleUrls: ['../assessment-instructions.scss'],
})
export class ExpandableSampleSolutionComponent implements OnInit {
    @Input() exercise: Exercise;
    @Input() isCollapsed = false;

    readonly ExerciseType_MODELING = ExerciseType.MODELING;
    formattedSampleSolutionExplanation: SafeHtml | null;
    modelingSampleSolution: UMLModel;
    sampleSolution: string;
    diagramType: UMLDiagramType;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit() {
        if (this.exercise.type === this.ExerciseType_MODELING) {
            this.exercise = this.assignModelingExercise(this.exercise as ModelingExercise);
        } else if (this.exercise.type === ExerciseType.FILE_UPLOAD || this.exercise.type === ExerciseType.TEXT) {
            this.sampleSolution = (this.exercise as FileUploadExercise).sampleSolution;
        }
    }

    assignModelingExercise(modelingExercise: ModelingExercise): Exercise {
        if (modelingExercise.sampleSolutionModel) {
            this.modelingSampleSolution = JSON.parse(modelingExercise.sampleSolutionModel);
        }
        if (modelingExercise.sampleSolutionExplanation) {
            this.formattedSampleSolutionExplanation = this.artemisMarkdown.safeHtmlForMarkdown(modelingExercise.sampleSolutionExplanation);
        }
        this.diagramType = modelingExercise.diagramType;
        return modelingExercise;
    }
}
