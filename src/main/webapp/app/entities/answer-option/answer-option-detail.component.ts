import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IAnswerOption } from 'app/shared/model/answer-option.model';

@Component({
    selector: 'jhi-answer-option-detail',
    templateUrl: './answer-option-detail.component.html'
})
export class AnswerOptionDetailComponent implements OnInit {
    answerOption: IAnswerOption;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ answerOption }) => {
            this.answerOption = answerOption;
        });
    }

    previousState() {
        window.history.back();
    }
}
