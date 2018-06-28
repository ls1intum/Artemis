import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { QuizSubmission } from './quiz-submission.model';
import { QuizSubmissionPopupService } from './quiz-submission-popup.service';
import { QuizSubmissionService } from './quiz-submission.service';

@Component({
    selector: 'jhi-quiz-submission-dialog',
    templateUrl: './quiz-submission-dialog.component.html'
})
export class QuizSubmissionDialogComponent implements OnInit {

    quizSubmission: QuizSubmission;
    isSaving: boolean;

    constructor(
        public activeModal: NgbActiveModal,
        private quizSubmissionService: QuizSubmissionService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.quizSubmission.id !== undefined) {
            this.subscribeToSaveResponse(
                this.quizSubmissionService.update(this.quizSubmission));
        } else {
            this.subscribeToSaveResponse(
                this.quizSubmissionService.create(this.quizSubmission));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<QuizSubmission>>) {
        result.subscribe((res: HttpResponse<QuizSubmission>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: QuizSubmission) {
        this.eventManager.broadcast({ name: 'quizSubmissionListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }
}

@Component({
    selector: 'jhi-quiz-submission-popup',
    template: ''
})
export class QuizSubmissionPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private quizSubmissionPopupService: QuizSubmissionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.quizSubmissionPopupService
                    .open(QuizSubmissionDialogComponent as Component, params['id']);
            } else {
                this.quizSubmissionPopupService
                    .open(QuizSubmissionDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
