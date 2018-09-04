import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';

import { InstructorDashboardPopupService } from './instructor-dashboard-popup.service';
import { Exercise, ExerciseService } from '../entities/exercise';

@Component({
    selector: 'jhi-instructor-dashboard-cleanup-dialog',
    templateUrl: './instructor-dashboard-cleanup-dialog.component.html'
})
export class InstructorDashboardCleanupDialogComponent {

    exercise: Exercise;
    confirmExerciseName: string;
    deleteRepositories: boolean;
    cleanupInProgress: boolean;
    deleteInProgress: boolean;

    constructor(
        private exerciseService: ExerciseService,
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService
    ) {
        this.confirmExerciseName = '';
        this.deleteRepositories = false;
        this.cleanupInProgress = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmCleanup(id: number) {
        this.cleanupInProgress = true;
        this.exerciseService.cleanup(id, this.deleteRepositories).subscribe(
            response => {
                this.deleteInProgress = false;
                if (this.deleteRepositories) {
                    this.jhiAlertService.success('Cleanup was successful. All build plans and repositories have been deleted. All participations have been marked as Finished.');
                } else {
                    this.jhiAlertService.success('Cleanup was successful. All build plans have been deleted. Students can resume their participation.');
                }
                this.activeModal.dismiss(true);
                const blob = new Blob([response.body], { type: 'application/zip' });
                const url = window.URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.setAttribute('href', url);
                link.setAttribute('download', response.headers.get('filename'));
                document.body.appendChild(link); // Required for FF
                link.click();
                window.URL.revokeObjectURL(url);
            },
            err => {
                this.deleteInProgress = false;
            });
    }
}

@Component({
    selector: 'jhi-instructor-dashboard-cleanup-popup',
    template: ''
})
export class InstructorDashboardCleanupPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private instructorDashboardPopupService: InstructorDashboardPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.instructorDashboardPopupService
                .open(InstructorDashboardCleanupDialogComponent as Component, params['id'], true);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
