import { Component, Input, OnInit, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { catchError, tap } from 'rxjs/operators';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { SubmissionExportOptions, SubmissionExportService } from './submission-export.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-exercise-submission-export-dialog',
    templateUrl: './submission-export-dialog.component.html',
    styles: ['textarea { width: 100%; }'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, FormDateTimePickerComponent, FaIconComponent],
})
export class SubmissionExportDialogComponent implements OnInit {
    private exerciseService = inject(ExerciseService);
    private submissionExportService = inject(SubmissionExportService);
    activeModal = inject(NgbActiveModal);
    private alertService = inject(AlertService);

    @Input() exerciseId: number;
    @Input() exerciseType: ExerciseType;

    exercise: Exercise;
    exportInProgress: boolean;
    submissionExportOptions: SubmissionExportOptions;
    isLoading = false;

    // Icons
    faCircleNotch = faCircleNotch;

    ngOnInit() {
        this.isLoading = true;
        this.exportInProgress = false;
        this.submissionExportOptions = {
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
                    this.alertService.error(err);
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

    exportSubmissions(exerciseId: number) {
        this.exportInProgress = true;
        this.submissionExportService.exportSubmissions(exerciseId, this.exerciseType, this.submissionExportOptions).subscribe({
            next: this.handleExportResponse,
            error: () => {
                this.exportInProgress = false;
            },
        });
    }

    handleExportResponse = (response: HttpResponse<Blob>) => {
        this.alertService.success('artemisApp.instructorDashboard.exportSubmissions.successMessage');
        this.activeModal.dismiss(true);
        this.exportInProgress = false;
        downloadZipFileFromResponse(response);
    };
}
