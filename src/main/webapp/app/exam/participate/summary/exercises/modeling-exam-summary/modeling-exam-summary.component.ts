import { Component, OnInit, Input, OnChanges, SimpleChanges } from '@angular/core';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { UMLModel } from '@ls1intum/apollon';

@Component({
    selector: 'jhi-modeling-exam-summary',
    templateUrl: './modeling-exam-summary.component.html',
    styles: [],
})
export class ModelingExamSummaryComponent implements OnChanges {
    @Input()
    exercise: ModelingExercise;

    @Input()
    submission: ModelingSubmission;

    umlModel: UMLModel;

    constructor() {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.submission.currentValue !== changes.submission.previousValue) {
            this.umlModel = JSON.parse(this.submission.model);
        }
    }
}
