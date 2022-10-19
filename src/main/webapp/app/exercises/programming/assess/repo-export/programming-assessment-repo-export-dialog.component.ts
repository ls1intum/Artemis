import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingAssessmentRepoExportService, RepositoryExportOptions } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export.service';
import { catchError, tap } from 'rxjs/operators';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { downloadZipFileFromResponse } from 'app/shared/util/download.util';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-exercise-scores-repo-export-dialog',
    templateUrl: './programming-assessment-repo-export-dialog.component.html',
    styles: ['textarea { width: 100%; }'],
})
export class ProgrammingAssessmentRepoExportDialogComponent implements OnInit {
    @Input() exerciseId: number;
    // Either a participationId list or a participantIdentifier (student login or team short name) list can be provided that is used for exporting the repos.
    // Priority: participationId >> participantIdentifier.
    @Input() participationIdList: number[];
    @Input() participantIdentifierList: string; // TODO: Should be a list and not a comma separated string.
    @Input() singleParticipantMode = false;
    // In case of exporting multiple exercises, a list of the selected exercises must be passed
    selectedProgrammingExercises: ProgrammingExercise[] = [];
    readonly FeatureToggle = FeatureToggle;
    exercise: Exercise;
    exportInProgress: boolean;
    repositoryExportOptions: RepositoryExportOptions;
    isLoading = false;
    isRepoExportForMultipleExercises: boolean;
    isAtLeastInstructor = false;

    // Icons
    faCircleNotch = faCircleNotch;

    constructor(
        private exerciseService: ExerciseService,
        private repoExportService: ProgrammingAssessmentRepoExportService,
        public activeModal: NgbActiveModal,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        this.isLoading = true;
        this.exportInProgress = false;
        this.repositoryExportOptions = {
            exportAllParticipants: false,
            filterLateSubmissions: false,
            addParticipantName: true,
            combineStudentCommits: true,
            anonymizeStudentCommits: false,
            normalizeCodeStyle: false, // disabled by default because it is rather unstable
            hideStudentNameInZippedFolder: false,
        };
        this.isRepoExportForMultipleExercises = this.selectedProgrammingExercises.length > 0;
        if (!this.isRepoExportForMultipleExercises) {
            this.exerciseService
                .find(this.exerciseId)
                .pipe(
                    tap(({ body: exercise }) => {
                        this.exercise = exercise!;
                        this.isAtLeastInstructor = exercise!.isAtLeastInstructor ? exercise!.isAtLeastInstructor : false;
                    }),
                    catchError((err) => {
                        this.alertService.error(err);
                        this.clear();
                        return of(undefined);
                    }),
                )
                .subscribe(() => {
                    this.isLoading = false;
                });
        } else {
            this.isAtLeastInstructor = this.selectedProgrammingExercises.every((exercise) => exercise.isAtLeastInstructor);
            this.isLoading = false;
        }
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    exportRepos(exerciseId?: number) {
        if (!exerciseId) {
            return;
        }
        this.exportInProgress = true;
        // The participation ids take priority over the participant identifiers (student login or team names).
        if (this.participationIdList?.length) {
            // We anonymize the assessment process ("double-blind").
            this.repositoryExportOptions.addParticipantName = false;
            this.repositoryExportOptions.hideStudentNameInZippedFolder = true;
            this.repoExportService.exportReposByParticipations(exerciseId, this.participationIdList, this.repositoryExportOptions).subscribe({
                next: this.handleExportRepoResponse,
                error: () => {
                    this.exportInProgress = false;
                },
            });
            return;
        }
        const participantIdentifierList =
            this.participantIdentifierList !== undefined && this.participantIdentifierList !== '' ? this.participantIdentifierList.split(',').map((e) => e.trim()) : ['ALL'];

        this.repoExportService.exportReposByParticipantIdentifiers(exerciseId, participantIdentifierList, this.repositoryExportOptions).subscribe({
            next: this.handleExportRepoResponse,
            error: () => {
                this.exportInProgress = false;
            },
        });
    }

    bulkExportRepos() {
        this.repositoryExportOptions.exportAllParticipants = true;
        this.selectedProgrammingExercises.forEach((programmingExercise) => {
            this.exportRepos(programmingExercise.id);
        });
    }

    handleExportRepoResponse = (response: HttpResponse<Blob>) => {
        this.alertService.success('artemisApp.programmingExercise.export.successMessageRepos');
        this.activeModal.dismiss(true);
        this.exportInProgress = false;
        downloadZipFileFromResponse(response);
    };
}
