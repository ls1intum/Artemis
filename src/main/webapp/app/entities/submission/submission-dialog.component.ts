import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { Submission } from './submission.model';
import { SubmissionPopupService } from './submission-popup.service';
import { SubmissionService } from './submission.service';

@Component({
    selector: 'jhi-submission-dialog',
    templateUrl: './submission-dialog.component.html'
})
export class SubmissionDialogComponent implements OnInit {

    submission: Submission;
    isSaving: boolean;

    constructor(
        public activeModal: NgbActiveModal,
        private submissionService: SubmissionService,
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
        if (this.submission.id !== undefined) {
            this.subscribeToSaveResponse(
                this.submissionService.update(this.submission));
        } else {
            this.subscribeToSaveResponse(
                this.submissionService.create(this.submission));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Submission>>) {
        result.subscribe((res: HttpResponse<Submission>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: Submission) {
        this.eventManager.broadcast({ name: 'submissionListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }
}

@Component({
    selector: 'jhi-submission-popup',
    template: ''
})
export class SubmissionPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private submissionPopupService: SubmissionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.submissionPopupService
                    .open(SubmissionDialogComponent as Component, params['id']);
            } else {
                this.submissionPopupService
                    .open(SubmissionDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
