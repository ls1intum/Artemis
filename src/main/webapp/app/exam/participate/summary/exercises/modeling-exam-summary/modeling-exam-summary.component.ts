import { Component, Input, OnInit } from '@angular/core';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { UMLModel } from '@ls1intum/apollon';

@Component({
    selector: 'jhi-modeling-exam-summary',
    templateUrl: './modeling-exam-summary.component.html',
    styles: [],
})
export class ModelingExamSummaryComponent implements OnInit {
    @Input()
    exercise: ModelingExercise;

    @Input()
    submission: ModelingSubmission;

    umlModel: UMLModel;
    maxWidth: number;
    maxHeight: number;

    constructor() {}

    ngOnInit() {
        if (this.submission && this.submission.model) {
            this.umlModel = JSON.parse(this.submission.model);
            // TODO: fix this when we have a more reliable solution (currently diagram is not centered)
            // margin on all sides
            const margin = 100;
            this.maxWidth = this.umlModel.size.width += 2 * margin;
            this.maxHeight = this.umlModel.size.height += 2 * margin;
            this.umlModel.elements.forEach((element) => {
                element.bounds.x += margin / 2;
                element.bounds.y += margin / 2;
            });
        }
    }
}
