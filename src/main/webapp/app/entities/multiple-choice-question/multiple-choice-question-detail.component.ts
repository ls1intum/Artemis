import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IMultipleChoiceQuestion } from 'app/shared/model/multiple-choice-question.model';

@Component({
    selector: 'jhi-multiple-choice-question-detail',
    templateUrl: './multiple-choice-question-detail.component.html'
})
export class MultipleChoiceQuestionDetailComponent implements OnInit {
    multipleChoiceQuestion: IMultipleChoiceQuestion;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ multipleChoiceQuestion }) => {
            this.multipleChoiceQuestion = multipleChoiceQuestion;
        });
    }

    previousState() {
        window.history.back();
    }
}
