import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';

import { ExerciseScoresPopupService } from './exercise-scores-popup.service';
import { Exercise, ExerciseService } from '../entities/exercise';
import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-exercise-scores-repo-export-dialog',
    templateUrl: './exercise-scores-repo-export-dialog.component.html',
    styles: ['textarea { width: 100%; }'],
})
export class ExerciseScoresRepoExportComponent {
    exercise: Exercise;
    exportInProgress: boolean;
    studentIdList: string;

    constructor(private exerciseService: ExerciseService, public activeModal: NgbActiveModal, private jhiAlertService: JhiAlertService) {
        this.exportInProgress = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    exportRepos(exerciseId: number) {
        this.exportInProgress = true;
        const studentIdList = this.studentIdList.split(',').map(e => e.trim());
        this.exerciseService.exportRepos(exerciseId, studentIdList).subscribe(
            response => {
                this.jhiAlertService.success('Export of repos was successful. The exported zip file with all repositories is currently being downloaded');
                this.activeModal.dismiss(true);
                this.exportInProgress = false;
                if (response.body) {
                    const zipFile = new Blob([response.body], { type: 'application/zip' });
                    const url = window.URL.createObjectURL(zipFile);
                    const link = document.createElement('a');
                    link.setAttribute('href', url);
                    link.setAttribute('download', response.headers.get('filename')!);
                    document.body.appendChild(link); // Required for FF
                    link.click();
                    window.URL.revokeObjectURL(url);
                }
            },
            err => {
                this.exportInProgress = false;
            },
        );
    }
}

@Component({
    selector: 'jhi-exercise-scores-export-repos-popup',
    template: '',
})
export class ExerciseScoresRepoExportPopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private route: ActivatedRoute, private instructorDashboardPopupService: ExerciseScoresPopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.instructorDashboardPopupService.open(ExerciseScoresRepoExportComponent as Component, params['id'], true);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
