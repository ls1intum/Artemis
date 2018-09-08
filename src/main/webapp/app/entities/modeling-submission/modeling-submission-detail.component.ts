import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IModelingSubmission } from 'app/shared/model/modeling-submission.model';

@Component({
    selector: 'jhi-modeling-submission-detail',
    templateUrl: './modeling-submission-detail.component.html'
})
export class ModelingSubmissionDetailComponent implements OnInit {
    modelingSubmission: IModelingSubmission;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ modelingSubmission }) => {
            this.modelingSubmission = modelingSubmission;
        });
    }

    previousState() {
        window.history.back();
    }
}
