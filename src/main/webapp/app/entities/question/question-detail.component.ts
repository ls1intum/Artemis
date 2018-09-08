import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IQuestion } from 'app/shared/model/question.model';

@Component({
    selector: 'jhi-question-detail',
    templateUrl: './question-detail.component.html'
})
export class QuestionDetailComponent implements OnInit {
    question: IQuestion;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ question }) => {
            this.question = question;
        });
    }

    previousState() {
        window.history.back();
    }
}
