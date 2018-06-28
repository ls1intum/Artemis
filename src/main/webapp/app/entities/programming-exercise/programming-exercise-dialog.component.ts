import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExercisePopupService } from './programming-exercise-popup.service';
import { ProgrammingExerciseService } from './programming-exercise.service';

@Component({
    selector: 'jhi-programming-exercise-dialog',
    templateUrl: './programming-exercise-dialog.component.html'
})
export class ProgrammingExerciseDialogComponent implements OnInit {

    programmingExercise: ProgrammingExercise;
    isSaving: boolean;

    constructor(
        public activeModal: NgbActiveModal,
        private programmingExerciseService: ProgrammingExerciseService,
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
        if (this.programmingExercise.id !== undefined) {
            this.subscribeToSaveResponse(
                this.programmingExerciseService.update(this.programmingExercise));
        } else {
            this.subscribeToSaveResponse(
                this.programmingExerciseService.create(this.programmingExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<ProgrammingExercise>>) {
        result.subscribe((res: HttpResponse<ProgrammingExercise>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: ProgrammingExercise) {
        this.eventManager.broadcast({ name: 'programmingExerciseListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }
}

@Component({
    selector: 'jhi-programming-exercise-popup',
    template: ''
})
export class ProgrammingExercisePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private programmingExercisePopupService: ProgrammingExercisePopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.programmingExercisePopupService
                    .open(ProgrammingExerciseDialogComponent as Component, params['id']);
            } else {
                this.programmingExercisePopupService
                    .open(ProgrammingExerciseDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
