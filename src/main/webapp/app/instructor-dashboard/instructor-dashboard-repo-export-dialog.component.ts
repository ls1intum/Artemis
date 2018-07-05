import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';

import { InstructorDashboardPopupService } from './instructor-dashboard-popup.service';
import { Exercise, ExerciseService } from '../entities/exercise';

@Component({
    selector: 'jhi-instructor-dashboard-repo-export-dialog',
    templateUrl: './instructor-dashboard-repo-export-dialog.component.html',
    styles: ['textarea { width: 100%; }']
})

export class InstructorDashboardExportReposComponent {

    exercise: Exercise;
    confirmExerciseName;
    exportInProgress;

    constructor(
        private exerciseService: ExerciseService,
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService
    ) {
        this.confirmExerciseName = '';
        this.exportInProgress = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    exportRepos(id: number) {
        this.exportInProgress = true;
        const list = this.list.split(',').map( e => e.trim());
        this.exerciseService.exportRepos(id, list).subscribe(
          response => {
              this.jhiAlertService.success(
                  'Export of repos was successful. The exported zip file with all repositories is currently being downloaded'
              );
              this.activeModal.dismiss(true);
              this.exportInProgress = false;
              if (response.body) {
                const blob = new Blob([response.body], { type: 'application/zip' });
                const url = window.URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.setAttribute('href', url);
                link.setAttribute('download', response.headers.get('filename'));
                document.body.appendChild(link); // Required for FF
                link.click();
                window.URL.revokeObjectURL(url);
              }
          },
          err => {
              this.archiveInProgress = false;
          });
        )
    }
}

@Component({
    selector: 'jhi-instructor-dashboard-export-repos-popup',
    template: ''
})
export class InstructorDashboardExportReposPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private instructorDashboardPopupService: InstructorDashboardPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.instructorDashboardPopupService
                .open(InstructorDashboardExportReposComponent as Component, params['id'], true);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
