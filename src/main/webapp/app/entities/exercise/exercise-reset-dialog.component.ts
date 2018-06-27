import { Component, OnDestroy, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from './exercise.model';
import { ActivatedRoute } from '@angular/router';
import { ExercisePopupService } from './exercise-popup.service';
import { ExerciseService } from './exercise.service';

@Component({
    selector: 'jhi-exercise-reset-dialog',
    templateUrl: './exercise-reset-dialog.component.html'
})
export class ExerciseResetDialogComponent implements OnInit {

    exercise: Exercise;
    confirmExerciseName: string;
    deleteParticipations: boolean;
    resetInProgress: boolean;

    constructor(
        public activeModal: NgbActiveModal,
        public exerciseService: ExerciseService
    ) {
    }

    ngOnInit() {
        this.deleteParticipations = false;
        this.resetInProgress = false;
        this.confirmExerciseName = '';
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmReset(id) {
        this.resetInProgress = true;
        this.exerciseService.reset(id).subscribe(() => {
            this.activeModal.close(true);
            this.resetInProgress = false;
        }, () => {
            this.resetInProgress = false;
        });
    }
}

@Component({
    selector: 'jhi-exercise-reset-popup',
    template: ''
})
export class ExerciseResetPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private exercisePopupService: ExercisePopupService
    ) {
    }

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.exercisePopupService
                .open(ExerciseResetDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
