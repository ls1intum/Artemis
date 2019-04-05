import { Component, Input, OnInit } from '@angular/core';
import { ModelingExercise } from 'app/entities/modeling-exercise';

@Component({
    selector: 'jhi-expandable-sample-solution',
    templateUrl: './expandable-sample-solution.component.html',
    styleUrls: ['../assessment-instructions.scss'],
})
export class ExpandableSampleSolutionComponent implements OnInit {
    @Input() exercise: ModelingExercise;
    @Input() isCollapsed = false;
    constructor() {}

    ngOnInit() {}
}
