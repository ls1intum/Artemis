import { Component, Input, OnInit } from '@angular/core';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { UMLModel } from '@ls1intum/apollon';

@Component({
    selector: 'jhi-expandable-sample-solution',
    templateUrl: './expandable-sample-solution.component.html',
    styleUrls: ['../assessment-instructions.scss'],
})
export class ExpandableSampleSolutionComponent implements OnInit {
    @Input() exercise: ModelingExercise;
    @Input() isCollapsed = false;

    formattedSampleSolutionExplanation: string;
    sampleSolution: UMLModel;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit() {
        if (this.exercise) {
            if (this.exercise.sampleSolutionModel) {
                this.sampleSolution = JSON.parse(this.exercise.sampleSolutionModel);
            }
            if (this.exercise.sampleSolutionExplanation) {
                this.formattedSampleSolutionExplanation = this.artemisMarkdown.htmlForMarkdown(this.exercise.sampleSolutionExplanation);
            }
        }
    }
}
