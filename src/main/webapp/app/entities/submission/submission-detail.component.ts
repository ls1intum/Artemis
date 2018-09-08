import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ISubmission } from 'app/shared/model/submission.model';

@Component({
    selector: 'jhi-submission-detail',
    templateUrl: './submission-detail.component.html'
})
export class SubmissionDetailComponent implements OnInit {
    submission: ISubmission;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ submission }) => {
            this.submission = submission;
        });
    }

    previousState() {
        window.history.back();
    }
}
