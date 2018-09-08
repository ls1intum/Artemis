import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IMultipleChoiceSubmittedAnswer } from 'app/shared/model/multiple-choice-submitted-answer.model';

@Component({
    selector: 'jhi-multiple-choice-submitted-answer-detail',
    templateUrl: './multiple-choice-submitted-answer-detail.component.html'
})
export class MultipleChoiceSubmittedAnswerDetailComponent implements OnInit {
    multipleChoiceSubmittedAnswer: IMultipleChoiceSubmittedAnswer;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ multipleChoiceSubmittedAnswer }) => {
            this.multipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswer;
        });
    }

    previousState() {
        window.history.back();
    }
}
