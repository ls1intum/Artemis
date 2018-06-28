import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { SubmittedAnswer } from './submitted-answer.model';
import { SubmittedAnswerPopupService } from './submitted-answer-popup.service';
import { SubmittedAnswerService } from './submitted-answer.service';
import { Question, QuestionService } from '../question';
import { QuizSubmission, QuizSubmissionService } from '../quiz-submission';

@Component({
    selector: 'jhi-submitted-answer-dialog',
    templateUrl: './submitted-answer-dialog.component.html'
})
export class SubmittedAnswerDialogComponent implements OnInit {

    submittedAnswer: SubmittedAnswer;
    isSaving: boolean;

    questions: Question[];

    quizsubmissions: QuizSubmission[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private submittedAnswerService: SubmittedAnswerService,
        private questionService: QuestionService,
        private quizSubmissionService: QuizSubmissionService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.questionService.query()
            .subscribe((res: HttpResponse<Question[]>) => { this.questions = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
        this.quizSubmissionService.query()
            .subscribe((res: HttpResponse<QuizSubmission[]>) => { this.quizsubmissions = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.submittedAnswer.id !== undefined) {
            this.subscribeToSaveResponse(
                this.submittedAnswerService.update(this.submittedAnswer));
        } else {
            this.subscribeToSaveResponse(
                this.submittedAnswerService.create(this.submittedAnswer));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<SubmittedAnswer>>) {
        result.subscribe((res: HttpResponse<SubmittedAnswer>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: SubmittedAnswer) {
        this.eventManager.broadcast({ name: 'submittedAnswerListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackQuestionById(index: number, item: Question) {
        return item.id;
    }

    trackQuizSubmissionById(index: number, item: QuizSubmission) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-submitted-answer-popup',
    template: ''
})
export class SubmittedAnswerPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private submittedAnswerPopupService: SubmittedAnswerPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.submittedAnswerPopupService
                    .open(SubmittedAnswerDialogComponent as Component, params['id']);
            } else {
                this.submittedAnswerPopupService
                    .open(SubmittedAnswerDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
