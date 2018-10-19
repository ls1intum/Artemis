import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ITextSubmission } from 'app/shared/model/text-submission.model';

@Component({
    selector: 'jhi-text-submission-detail',
    templateUrl: './text-submission-detail.component.html'
})
export class TextSubmissionDetailComponent implements OnInit {
    textSubmission: ITextSubmission;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ textSubmission }) => {
            this.textSubmission = textSubmission;
        });
    }

    previousState() {
        window.history.back();
    }
}
