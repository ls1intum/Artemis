import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExercisePopupService } from './programming-exercise-popup.service';
import { ProgrammingExerciseService } from './programming-exercise.service';

import { Subscription } from 'rxjs/Subscription';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-programming-exercise-delete-dialog',
    templateUrl: './programming-exercise-delete-dialog.component.html'
})
export class ProgrammingExerciseDeleteDialogComponent {
    programmingExercise: ProgrammingExercise;
    confirmExerciseName: string;
    deleteBaseReposBuildPlans = false;
    deleteStudentReposBuildPlans = true;
    deleteInProgress = false;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager,
        private jhiAlertService: JhiAlertService
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.deleteInProgress = true;
        this.programmingExerciseService.delete(id, this.deleteStudentReposBuildPlans, this.deleteBaseReposBuildPlans).subscribe(
            response => {
                this.deleteInProgress = false;
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted an programmingExercise'
                });
                // this.jhiAlertService.success('Delete was successful');
                this.activeModal.dismiss(true);
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
                this.deleteInProgress = false;
            }
        );
    }
}

@Component({
    selector: 'jhi-programming-exercise-delete-popup',
    template: ''
})
export class ProgrammingExerciseDeletePopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private route: ActivatedRoute, private programmingExercisePopupService: ProgrammingExercisePopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.programmingExercisePopupService.open(ProgrammingExerciseDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
