import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IQuizSubmission } from 'app/shared/model/quiz-submission.model';

@Component({
    selector: 'jhi-quiz-submission-detail',
    templateUrl: './quiz-submission-detail.component.html'
})
export class QuizSubmissionDetailComponent implements OnInit {
    quizSubmission: IQuizSubmission;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ quizSubmission }) => {
            this.quizSubmission = quizSubmission;
        });
    }

    previousState() {
        window.history.back();
    }
}
