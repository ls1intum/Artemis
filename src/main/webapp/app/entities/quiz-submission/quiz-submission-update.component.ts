import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IQuizSubmission } from 'app/shared/model/quiz-submission.model';
import { QuizSubmissionService } from './quiz-submission.service';

@Component({
    selector: 'jhi-quiz-submission-update',
    templateUrl: './quiz-submission-update.component.html'
})
export class QuizSubmissionUpdateComponent implements OnInit {
    quizSubmission: IQuizSubmission;
    isSaving: boolean;

    constructor(private quizSubmissionService: QuizSubmissionService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ quizSubmission }) => {
            this.quizSubmission = quizSubmission;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.quizSubmission.id !== undefined) {
            this.subscribeToSaveResponse(this.quizSubmissionService.update(this.quizSubmission));
        } else {
            this.subscribeToSaveResponse(this.quizSubmissionService.create(this.quizSubmission));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IQuizSubmission>>) {
        result.subscribe((res: HttpResponse<IQuizSubmission>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }
}
