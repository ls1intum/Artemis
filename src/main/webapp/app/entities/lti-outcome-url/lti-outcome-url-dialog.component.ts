import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { LtiOutcomeUrl } from './lti-outcome-url.model';
import { LtiOutcomeUrlPopupService } from './lti-outcome-url-popup.service';
import { LtiOutcomeUrlService } from './lti-outcome-url.service';
import { User, UserService } from '../../shared';
import { Exercise, ExerciseService } from '../exercise';

@Component({
    selector: 'jhi-lti-outcome-url-dialog',
    templateUrl: './lti-outcome-url-dialog.component.html'
})
export class LtiOutcomeUrlDialogComponent implements OnInit {

    ltiOutcomeUrl: LtiOutcomeUrl;
    isSaving: boolean;

    users: User[];

    exercises: Exercise[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private ltiOutcomeUrlService: LtiOutcomeUrlService,
        private userService: UserService,
        private exerciseService: ExerciseService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.userService.query()
            .subscribe((res: HttpResponse<User[]>) => { this.users = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
        this.exerciseService.query()
            .subscribe((res: HttpResponse<Exercise[]>) => { this.exercises = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.ltiOutcomeUrl.id !== undefined) {
            this.subscribeToSaveResponse(
                this.ltiOutcomeUrlService.update(this.ltiOutcomeUrl));
        } else {
            this.subscribeToSaveResponse(
                this.ltiOutcomeUrlService.create(this.ltiOutcomeUrl));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<LtiOutcomeUrl>>) {
        result.subscribe((res: HttpResponse<LtiOutcomeUrl>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: LtiOutcomeUrl) {
        this.eventManager.broadcast({ name: 'ltiOutcomeUrlListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackUserById(index: number, item: User) {
        return item.id;
    }

    trackExerciseById(index: number, item: Exercise) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-lti-outcome-url-popup',
    template: ''
})
export class LtiOutcomeUrlPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private ltiOutcomeUrlPopupService: LtiOutcomeUrlPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.ltiOutcomeUrlPopupService
                    .open(LtiOutcomeUrlDialogComponent as Component, params['id']);
            } else {
                this.ltiOutcomeUrlPopupService
                    .open(LtiOutcomeUrlDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
