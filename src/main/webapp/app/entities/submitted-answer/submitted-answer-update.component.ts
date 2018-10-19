import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { ISubmittedAnswer } from 'app/shared/model/submitted-answer.model';
import { SubmittedAnswerService } from './submitted-answer.service';
import { IQuestion } from 'app/shared/model/question.model';
import { QuestionService } from 'app/entities/question';
import { IQuizSubmission } from 'app/shared/model/quiz-submission.model';
import { QuizSubmissionService } from 'app/entities/quiz-submission';

@Component({
    selector: 'jhi-submitted-answer-update',
    templateUrl: './submitted-answer-update.component.html'
})
export class SubmittedAnswerUpdateComponent implements OnInit {
    submittedAnswer: ISubmittedAnswer;
    isSaving: boolean;

    questions: IQuestion[];

    quizsubmissions: IQuizSubmission[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private submittedAnswerService: SubmittedAnswerService,
        private questionService: QuestionService,
        private quizSubmissionService: QuizSubmissionService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ submittedAnswer }) => {
            this.submittedAnswer = submittedAnswer;
        });
        this.questionService.query().subscribe(
            (res: HttpResponse<IQuestion[]>) => {
                this.questions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
        this.quizSubmissionService.query().subscribe(
            (res: HttpResponse<IQuizSubmission[]>) => {
                this.quizsubmissions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.submittedAnswer.id !== undefined) {
            this.subscribeToSaveResponse(this.submittedAnswerService.update(this.submittedAnswer));
        } else {
            this.subscribeToSaveResponse(this.submittedAnswerService.create(this.submittedAnswer));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ISubmittedAnswer>>) {
        result.subscribe((res: HttpResponse<ISubmittedAnswer>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }

    trackQuestionById(index: number, item: IQuestion) {
        return item.id;
    }

    trackQuizSubmissionById(index: number, item: IQuizSubmission) {
        return item.id;
    }
}
