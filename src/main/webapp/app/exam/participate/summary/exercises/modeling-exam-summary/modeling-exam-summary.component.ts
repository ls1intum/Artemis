import { Component, Input, OnInit } from '@angular/core';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { UMLModel } from '@ls1intum/apollon';

@Component({
    selector: 'jhi-modeling-exam-summary',
    templateUrl: './modeling-exam-summary.component.html',
})
export class ModelingExamSummaryComponent implements OnInit {
    @Input()
    exercise: ModelingExercise;

    @Input()
    submission: ModelingSubmission;

    umlModel: UMLModel;

    constructor() {}

    ngOnInit() {
        if (this.submission && this.submission.model) {
            this.umlModel = JSON.parse(this.submission.model);
        }
    }
}
