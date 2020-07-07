import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/alert/alert.service';
import { WindowRef } from 'app/core/websocket/window.service';
import { catchError, tap } from 'rxjs/operators';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { RepositoryExportOptions, TextSubmissionExportService } from './text-submission-export.service';

@Component({
    selector: 'jhi-text-exercise-submission-export-dialog',
    templateUrl: './text-submission-export-dialog.component.html',
    styles: ['textarea { width: 100%; }'],
})
export class TextSubmissionExportDialogComponent implements OnInit {
    @Input() exerciseId: number;

    exercise: Exercise;
    exportInProgress: boolean;
    repositoryExportOptions: RepositoryExportOptions;
    isLoading = false;

    constructor(
        private $window: WindowRef,
        private exerciseService: ExerciseService,
        private submissionExportService: TextSubmissionExportService,
        public activeModal: NgbActiveModal,
        private jhiAlertService: AlertService,
    ) {}

    ngOnInit() {
        this.isLoading = true;
        this.exportInProgress = false;
        this.repositoryExportOptions = {
            exportAllParticipants: false,
            filterLateSubmissions: false,
            filterLateSubmissionsDate: null,
            participantIdentifierList: '',
        };
        this.exerciseService
            .find(this.exerciseId)
            .pipe(
                tap(({ body: exercise }) => {
                    this.exercise = exercise!;
                }),
                catchError((err) => {
                    this.jhiAlertService.error(err);
                    this.clear();
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isLoading = false;
            });
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    exportRepos(exerciseId: number) {
        this.exportInProgress = true;
        this.submissionExportService.exportSubmissionsByParticipantIdentifiers(exerciseId, this.repositoryExportOptions).subscribe(this.handleExportRepoResponse, () => {
            this.exportInProgress = false;
        });
    }

    handleExportRepoResponse = (response: HttpResponse<Blob>) => {
        this.jhiAlertService.success('artemisApp.programmingExercise.export.successMessage');
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
    };
}
