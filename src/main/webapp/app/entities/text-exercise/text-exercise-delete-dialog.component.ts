import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { TextExercise } from './text-exercise.model';
import { TextExercisePopupService } from './text-exercise-popup.service';
import { TextExerciseService } from './text-exercise.service';

@Component({
    selector: 'jhi-text-exercise-delete-dialog',
    templateUrl: './text-exercise-delete-dialog.component.html'
})
export class TextExerciseDeleteDialogComponent {

    textExercise: TextExercise;

    constructor(
        private textExerciseService: TextExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.textExerciseService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'textExerciseListModification',
                content: 'Deleted an textExercise'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-text-exercise-delete-popup',
    template: ''
})
export class TextExerciseDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private textExercisePopupService: TextExercisePopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.textExercisePopupService
                .open(TextExerciseDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
