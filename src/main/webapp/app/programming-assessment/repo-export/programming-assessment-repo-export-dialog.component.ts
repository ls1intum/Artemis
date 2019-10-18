import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService } from 'ng-jhipster';

import { Moment } from 'moment';

import { ExerciseScoresPopupService } from '../../scores/exercise-scores-popup.service';
import { Exercise, ExerciseService } from '../../entities/exercise';
import { Subscription } from 'rxjs/Subscription';
import { WindowRef } from 'app/core/websocket/window.service';

export type RepositoryExportOptions = {
    exportAllStudents: boolean;
    filterLateSubmissions: boolean;
    filterLateSubmissionsDate: Moment | null;
    addStudentName: boolean;
    squashAfterInstructor: boolean;
    normalizeCodeStyle: boolean;
};

@Component({
    selector: 'jhi-exercise-scores-repo-export-dialog',
    templateUrl: './programming-assessment-repo-export-dialog.component.html',
    styles: ['textarea { width: 100%; }'],
})
export class ProgrammingAssessmentRepoExportDialogComponent {
    exercise: Exercise;
    exportInProgress: boolean;
    studentIdList: string;
    repositoryExportOptions: RepositoryExportOptions;

    constructor(private $window: WindowRef, private exerciseService: ExerciseService, public activeModal: NgbActiveModal, private jhiAlertService: JhiAlertService) {
        this.exportInProgress = false;
        this.repositoryExportOptions = {
            exportAllStudents: false,
            filterLateSubmissions: false,
            filterLateSubmissionsDate: null,
            addStudentName: true,
            squashAfterInstructor: true,
            normalizeCodeStyle: true,
        };
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    exportRepos(exerciseId: number) {
        this.exportInProgress = true;
        const studentIdList = this.studentIdList !== undefined && this.studentIdList !== '' ? this.studentIdList.split(',').map(e => e.trim()) : ['ALL'];
        this.exerciseService.exportRepos(exerciseId, studentIdList, this.repositoryExportOptions).subscribe(
            response => {
                this.jhiAlertService.success('Export of repos was successful. The exported zip file with all repositories is currently being downloaded');
                this.activeModal.dismiss(true);
                this.exportInProgress = false;
                if (response.body) {
                    const zipFile = new Blob([response.body], { type: 'application/zip' });
                    const url = this.$window.nativeWindow.URL.createObjectURL(zipFile);
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
