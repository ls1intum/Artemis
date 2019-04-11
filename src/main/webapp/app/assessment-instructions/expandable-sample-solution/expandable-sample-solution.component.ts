import { Component, Input, OnInit } from '@angular/core';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';

@Component({
    selector: 'jhi-expandable-sample-solution',
    templateUrl: './expandable-sample-solution.component.html',
    styleUrls: ['../assessment-instructions.scss'],
})
export class ExpandableSampleSolutionComponent implements OnInit {
    @Input() exercise: ModelingExercise;
    @Input() isCollapsed = false;

    formattedSampleSolutionExplanation: string;

    constructor(private artemisMarkdown: ArtemisMarkdown) {}

    ngOnInit() {
        this.formattedSampleSolutionExplanation = this.artemisMarkdown.htmlForMarkdown(this.exercise.sampleSolutionExplanation);
    }
}
