import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { Exercise } from './exercise.model';
import { ExercisePopupService } from './exercise-popup.service';
import { ExerciseService } from './exercise.service';

@Component({
    selector: 'jhi-exercise-delete-dialog',
    templateUrl: './exercise-delete-dialog.component.html'
})
export class ExerciseDeleteDialogComponent {

    exercise: Exercise;

    constructor(
        private exerciseService: ExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.exerciseService.delete(id).subscribe((response) => {
            this.eventManager.broadcast({
                name: 'exerciseListModification',
                content: 'Deleted an exercise'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-exercise-delete-popup',
    template: ''
})
export class ExerciseDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private exercisePopupService: ExercisePopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.exercisePopupService
                .open(ExerciseDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
