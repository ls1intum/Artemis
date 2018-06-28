import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { Participation } from './participation.model';
import { ParticipationPopupService } from './participation-popup.service';
import { ParticipationService } from './participation.service';
import { User, UserService } from '../../shared';
import { Exercise, ExerciseService } from '../exercise';

@Component({
    selector: 'jhi-participation-dialog',
    templateUrl: './participation-dialog.component.html'
})
export class ParticipationDialogComponent implements OnInit {

    participation: Participation;
    isSaving: boolean;

    users: User[];

    exercises: Exercise[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private participationService: ParticipationService,
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
        if (this.participation.id !== undefined) {
            this.subscribeToSaveResponse(
                this.participationService.update(this.participation));
        } else {
            this.subscribeToSaveResponse(
                this.participationService.create(this.participation));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Participation>>) {
        result.subscribe((res: HttpResponse<Participation>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: Participation) {
        this.eventManager.broadcast({ name: 'participationListModification', content: 'OK'});
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
    selector: 'jhi-participation-popup',
    template: ''
})
export class ParticipationPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private participationPopupService: ParticipationPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.participationPopupService
                    .open(ParticipationDialogComponent as Component, params['id']);
            } else {
                this.participationPopupService
                    .open(ParticipationDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
