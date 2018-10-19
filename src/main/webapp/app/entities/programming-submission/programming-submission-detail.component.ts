import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IProgrammingSubmission } from 'app/shared/model/programming-submission.model';

@Component({
    selector: 'jhi-programming-submission-detail',
    templateUrl: './programming-submission-detail.component.html'
})
export class ProgrammingSubmissionDetailComponent implements OnInit {
    programmingSubmission: IProgrammingSubmission;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingSubmission }) => {
            this.programmingSubmission = programmingSubmission;
        });
    }

    previousState() {
        window.history.back();
    }
}
