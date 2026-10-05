import { Component, OnInit, inject, signal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProgrammingAssessmentRepoExportService, RepositoryExportOptions } from 'app/programming/manage/assess/repo-export/programming-assessment-repo-export.service';
import { HttpResponse } from '@angular/common/http';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { downloadZipFileFromResponse } from 'app/foundation/util/download.util';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { FormDateTimePickerComponent } from 'app/shared-ui/date-time-picker/date-time-picker.component';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

interface ProgrammingAssessmentRepoExportDialogData {
    programmingExercises: ProgrammingExercise[];
    // Either a participationId list or a participantIdentifier (student login or team short name) list can be provided that is used for exporting the repos.
    // Priority: participationId >> participantIdentifier.
    participationIdList?: number[];
    participantIdentifierList?: string; // TODO: Should be a list and not a comma separated string.
    singleParticipantMode?: boolean;
}

@Component({
    selector: 'jhi-exercise-scores-repo-export-dialog',
    templateUrl: './programming-assessment-repo-export-dialog.component.html',
    styles: ['textarea { width: 100%; }'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, FormDateTimePickerComponent, FeatureToggleDirective, FaIconComponent],
})
export class ProgrammingAssessmentRepoExportDialogComponent implements OnInit {
    private repoExportService = inject(ProgrammingAssessmentRepoExportService);
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);
    private alertService = inject(AlertService);

    private readonly data = this.dialogConfig.data as ProgrammingAssessmentRepoExportDialogData | undefined;

    programmingExercises: ProgrammingExercise[] = this.data?.programmingExercises ?? [];
    // Either a participationId list or a participantIdentifier (student login or team short name) list can be provided that is used for exporting the repos.
    // Priority: participationId >> participantIdentifier.
    participationIdList: number[] = this.data?.participationIdList ?? [];
    participantIdentifierList: string = this.data?.participantIdentifierList ?? ''; // TODO: Should be a list and not a comma separated string.
    singleParticipantMode = this.data?.singleParticipantMode ?? false;
    readonly FeatureToggle = FeatureToggle;
    readonly exportInProgress = signal(false);
    // Backed by a signal because the template reads it (e.g. [disabled]) while [(ngModel)] mutates its
    // properties in place. The getter/setter facade keeps reads reactive without breaking two-way binding.
    private readonly _repositoryExportOptions = signal<RepositoryExportOptions>(undefined!);
    get repositoryExportOptions(): RepositoryExportOptions {
        return this._repositoryExportOptions();
    }
    set repositoryExportOptions(value: RepositoryExportOptions) {
        this._repositoryExportOptions.set(value);
    }
    readonly isLoading = signal(false);
    readonly isRepoExportForMultipleExercises = signal<boolean>(undefined!);
    readonly isAtLeastInstructor = signal(false);

    // Icons
    faCircleNotch = faCircleNotch;

    ngOnInit() {
        this.isLoading.set(true);
        this.exportInProgress.set(false);
        this.isRepoExportForMultipleExercises.set(this.programmingExercises.length > 1);
        this.isAtLeastInstructor.set(this.programmingExercises.every((exercise) => exercise.isAtLeastInstructor));
        this.isLoading.set(false);
        this.repositoryExportOptions = {
            exportAllParticipants: this.isRepoExportForMultipleExercises(),
            filterLateSubmissions: false,
            excludePracticeSubmissions: false,
            combineStudentCommits: true,
            // we anonymize the export for tutors (double-blind)
            anonymizeRepository: !this.isAtLeastInstructor(),
            addParticipantName: this.isAtLeastInstructor(),
            normalizeCodeStyle: false, // disabled by default because it is rather unstable
        };
    }

    clear() {
        this.dialogRef.close();
    }

    exportRepos() {
        // Blank entries would otherwise become empty path segments in the request URL. The dialog can also be opened
        // with nothing preselected (the exercise scores page does that), and exporting then asked the server for an
        // empty participant list, which answered 404 instead of telling the user that nothing was selected.
        const participantIdentifiers = this.repositoryExportOptions.exportAllParticipants
            ? ['ALL']
            : this.participantIdentifierList
                  .split(',')
                  .map((identifier) => identifier.trim())
                  .filter((identifier) => identifier.length > 0);
        if (!this.participationIdList?.length && participantIdentifiers.length === 0) {
            this.alertService.error('artemisApp.programmingExercise.export.noParticipantsSelected');
            return;
        }

        this.programmingExercises.forEach((exercise) => {
            if (!exercise.id) {
                return;
            }
            this.exportInProgress.set(true);
            // The participation ids take priority over the participant identifiers (student login or team names).
            if (this.participationIdList?.length) {
                this.repoExportService
                    .exportReposByParticipations(exercise.id, this.participationIdList, this.repositoryExportOptions)
                    .subscribe({
                        next: this.handleExportRepoResponseSuccess,
                        error: () => this.handleExportRepoResponseError(exercise.id!),
                    })
                    .add(() => this.dialogRef.close(true));
                return;
            }
            this.repoExportService
                .exportReposByParticipantIdentifiers(exercise.id, participantIdentifiers, this.repositoryExportOptions)
                .subscribe({
                    next: this.handleExportRepoResponseSuccess,
                    error: () => this.handleExportRepoResponseError(exercise.id!),
                })
                .add(() => this.dialogRef.close(true));
        });
    }

    handleExportRepoResponseError = (exerciseId: number) => {
        this.alertService.warning('artemisApp.programmingExercise.export.notFoundMessageRepos', { exerciseId });
        this.exportInProgress.set(false);
    };

    handleExportRepoResponseSuccess = (response: HttpResponse<Blob>) => {
        this.alertService.success('artemisApp.programmingExercise.export.successMessageRepos');
        this.exportInProgress.set(false);
        downloadZipFileFromResponse(response);
    };
}
