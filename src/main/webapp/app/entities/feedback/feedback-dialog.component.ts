import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { Feedback } from './feedback.model';
import { FeedbackPopupService } from './feedback-popup.service';
import { FeedbackService } from './feedback.service';
import { Result, ResultService } from '../result';

@Component({
    selector: 'jhi-feedback-dialog',
    templateUrl: './feedback-dialog.component.html'
})
export class FeedbackDialogComponent implements OnInit {

    feedback: Feedback;
    isSaving: boolean;

    results: Result[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private feedbackService: FeedbackService,
        private resultService: ResultService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.resultService.query()
            .subscribe((res: HttpResponse<Result[]>) => { this.results = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.feedback.id !== undefined) {
            this.subscribeToSaveResponse(
                this.feedbackService.update(this.feedback));
        } else {
            this.subscribeToSaveResponse(
                this.feedbackService.create(this.feedback));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Feedback>>) {
        result.subscribe((res: HttpResponse<Feedback>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: Feedback) {
        this.eventManager.broadcast({ name: 'feedbackListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackResultById(index: number, item: Result) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-feedback-popup',
    template: ''
})
export class FeedbackPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private feedbackPopupService: FeedbackPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.feedbackPopupService
                    .open(FeedbackDialogComponent as Component, params['id']);
            } else {
                this.feedbackPopupService
                    .open(FeedbackDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
