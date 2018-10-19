import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IAnswerCounter } from 'app/shared/model/answer-counter.model';

@Component({
    selector: 'jhi-answer-counter-detail',
    templateUrl: './answer-counter-detail.component.html'
})
export class AnswerCounterDetailComponent implements OnInit {
    answerCounter: IAnswerCounter;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ answerCounter }) => {
            this.answerCounter = answerCounter;
        });
    }

    previousState() {
        window.history.back();
    }
}
