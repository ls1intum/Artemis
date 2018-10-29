import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';

import { ExerciseDashboardPopupService } from '../../dashboard/exercise-dashboard-popup.service';
import { Exercise, ExerciseService } from '../exercise/index';

import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-instructor-dashboard-cleanup-dialog',
    templateUrl: './programming-exercise-cleanup-dialog.component.html'
})
export class ProgrammingExerciseCleanupDialogComponent {
    exercise: Exercise;
    confirmExerciseName = '';
    deleteRepositories = false;
    cleanupInProgress = false;
    deleteInProgress = false;

    constructor(private exerciseService: ExerciseService, public activeModal: NgbActiveModal, private jhiAlertService: JhiAlertService) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmCleanup(id: number) {
        this.cleanupInProgress = true;
        this.exerciseService.cleanup(id, this.deleteRepositories).subscribe(
            response => {
                this.deleteInProgress = false;
                if (this.deleteRepositories) {
                    this.jhiAlertService.success(
                        'Cleanup was successful. All build plans and repositories have been deleted. All participations have been marked as Finished.'
                    );
                } else {
                    this.jhiAlertService.success(
                        'Cleanup was successful. All build plans have been deleted. Students can resume their participation.'
                    );
                }
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
    selector: 'jhi-instructor-dashboard-cleanup-popup',
    template: ''
})
export class InstructorDashboardCleanupPopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private route: ActivatedRoute, private instructorDashboardPopupService: ExerciseDashboardPopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.instructorDashboardPopupService.open(ProgrammingExerciseCleanupDialogComponent as Component, params['id'], true);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
