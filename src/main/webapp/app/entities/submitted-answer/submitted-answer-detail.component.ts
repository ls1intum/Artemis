import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ISubmittedAnswer } from 'app/shared/model/submitted-answer.model';

@Component({
    selector: 'jhi-submitted-answer-detail',
    templateUrl: './submitted-answer-detail.component.html'
})
export class SubmittedAnswerDetailComponent implements OnInit {
    submittedAnswer: ISubmittedAnswer;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ submittedAnswer }) => {
            this.submittedAnswer = submittedAnswer;
        });
    }

    previousState() {
        window.history.back();
    }
}
