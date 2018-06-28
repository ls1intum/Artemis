import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ModelingSubmission } from './modeling-submission.model';
import { ModelingSubmissionPopupService } from './modeling-submission-popup.service';
import { ModelingSubmissionService } from './modeling-submission.service';

@Component({
    selector: 'jhi-modeling-submission-dialog',
    templateUrl: './modeling-submission-dialog.component.html'
})
export class ModelingSubmissionDialogComponent implements OnInit {

    modelingSubmission: ModelingSubmission;
    isSaving: boolean;

    constructor(
        public activeModal: NgbActiveModal,
        private modelingSubmissionService: ModelingSubmissionService,
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
        if (this.modelingSubmission.id !== undefined) {
            this.subscribeToSaveResponse(
                this.modelingSubmissionService.update(this.modelingSubmission));
        } else {
            this.subscribeToSaveResponse(
                this.modelingSubmissionService.create(this.modelingSubmission));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ModelingSubmission>>) {
        result.subscribe((res: HttpResponse<ModelingSubmission>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: ModelingSubmission) {
        this.eventManager.broadcast({ name: 'modelingSubmissionListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }
}

@Component({
    selector: 'jhi-modeling-submission-popup',
    template: ''
})
export class ModelingSubmissionPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private modelingSubmissionPopupService: ModelingSubmissionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.modelingSubmissionPopupService
                    .open(ModelingSubmissionDialogComponent as Component, params['id']);
            } else {
                this.modelingSubmissionPopupService
                    .open(ModelingSubmissionDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
