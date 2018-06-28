import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { Result } from './result.model';
import { ResultPopupService } from './result-popup.service';
import { ResultService } from './result.service';
import { Submission, SubmissionService } from '../submission';
import { Participation, ParticipationService } from '../participation';

@Component({
    selector: 'jhi-result-dialog',
    templateUrl: './result-dialog.component.html'
})
export class ResultDialogComponent implements OnInit {

    result: Result;
    isSaving: boolean;

    submissions: Submission[];

    participations: Participation[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private resultService: ResultService,
        private submissionService: SubmissionService,
        private participationService: ParticipationService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.submissionService
            .query({filter: 'result-is-null'})
            .subscribe((res: HttpResponse<Submission[]>) => {
                if (!this.result.submission || !this.result.submission.id) {
                    this.submissions = res.body;
                } else {
                    this.submissionService
                        .find(this.result.submission.id)
                        .subscribe((subRes: HttpResponse<Submission>) => {
                            this.submissions = [subRes.body].concat(res.body);
                        }, (subRes: HttpErrorResponse) => this.onError(subRes.message));
                }
            }, (res: HttpErrorResponse) => this.onError(res.message));
        this.participationService.query()
            .subscribe((res: HttpResponse<Participation[]>) => { this.participations = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.result.id !== undefined) {
            this.subscribeToSaveResponse(
                this.resultService.update(this.result));
        } else {
            this.subscribeToSaveResponse(
                this.resultService.create(this.result));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Result>>) {
        result.subscribe((res: HttpResponse<Result>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: Result) {
        this.eventManager.broadcast({ name: 'resultListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackSubmissionById(index: number, item: Submission) {
        return item.id;
    }

    trackParticipationById(index: number, item: Participation) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-result-popup',
    template: ''
})
export class ResultPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private resultPopupService: ResultPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.resultPopupService
                    .open(ResultDialogComponent as Component, params['id']);
            } else {
                this.resultPopupService
                    .open(ResultDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
