import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';

import { InstructorDashboardPopupService } from './instructor-dashboard-popup.service';
import { Exercise, ExerciseService } from '../entities/exercise';

import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-instructor-dashboard-archive-dialog',
    templateUrl: './instructor-dashboard-archive-dialog.component.html'
})
export class InstructorDashboardArchiveDialogComponent {
    exercise: Exercise;
    archiveInProgress: boolean;

    constructor(private exerciseService: ExerciseService, public activeModal: NgbActiveModal, private jhiAlertService: JhiAlertService) {
        this.archiveInProgress = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmArchive(id: number) {
        this.archiveInProgress = true;
        this.exerciseService.archive(id).subscribe(
            response => {
                this.jhiAlertService.success(
                    'Archive was successful. The archive zip file with all repositories is currently being downloaded'
                );
                this.activeModal.dismiss(true);
                this.archiveInProgress = false;
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
                this.archiveInProgress = false;
            }
        );
    }
}

@Component({
    selector: 'jhi-instructor-dashboard-archive-popup',
    template: ''
})
export class InstructorDashboardArchivePopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private route: ActivatedRoute, private instructorDashboardPopupService: InstructorDashboardPopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.instructorDashboardPopupService.open(InstructorDashboardArchiveDialogComponent as Component, params['id'], true);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
