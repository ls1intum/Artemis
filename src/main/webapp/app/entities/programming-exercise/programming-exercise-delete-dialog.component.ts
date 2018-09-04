import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExercisePopupService } from './programming-exercise-popup.service';
import { ProgrammingExerciseService } from './programming-exercise.service';

import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-programming-exercise-delete-dialog',
    templateUrl: './programming-exercise-delete-dialog.component.html'
})
export class ProgrammingExerciseDeleteDialogComponent {

    programmingExercise: ProgrammingExercise;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.programmingExerciseService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'programmingExerciseListModification',
                content: 'Deleted an programmingExercise'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-programming-exercise-delete-popup',
    template: ''
})
export class ProgrammingExerciseDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: Subscription;

    constructor(
        private route: ActivatedRoute,
        private programmingExercisePopupService: ProgrammingExercisePopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.programmingExercisePopupService
                .open(ProgrammingExerciseDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
