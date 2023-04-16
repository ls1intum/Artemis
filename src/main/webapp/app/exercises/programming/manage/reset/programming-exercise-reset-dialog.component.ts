import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseResetOptions, ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { faBan, faCircleNotch, faEraser, faSpinner } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-programming-exercise-reset-dialog',
    templateUrl: './programming-exercise-reset-dialog.component.html',
    styles: ['textarea { width: 100%; }'],
})
export class ProgrammingExerciseResetDialogComponent implements OnInit {
    readonly FeatureToggle = FeatureToggle;

    @Input() programmingExercise: ProgrammingExercise;

    programmingExerciseResetOptions: ProgrammingExerciseResetOptions;
    isLoading = false;
    resetInProgress: boolean;
    confirmText: string;

    versionControlName?: string;
    continuousIntegrationName?: string;

    // Icons
    faBan = faBan;
    faCircleNotch = faCircleNotch;
    faSpinner = faSpinner;
    faEraser = faEraser;

    constructor(
        private alertService: AlertService,
        private profileService: ProfileService,
        private programmingExerciseService: ProgrammingExerciseService,
        public activeModal: NgbActiveModal,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.isLoading = true;
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.versionControlName = profileInfo.versionControlName;
                this.continuousIntegrationName = profileInfo.continuousIntegrationName;
            }
        });
        this.resetInProgress = false;
        this.programmingExerciseResetOptions = {
            deleteBuildPlans: false,
            deleteStudentRepositories: false,
            deleteStudentParticipationsSubmissionsAndResults: false,
            recreateBuildPlans: false,
        };
        this.isLoading = false;
    }

    /**
     * Closes the active modal dialog and dismisses it with a 'cancel' reason
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Determines whether to show the undeleted artifacts warning message
     * @returns {boolean} true if the warning message should be displayed, false otherwise
     */
    showUndeletedArtifactsWarning() {
        const options = [
            this.programmingExerciseResetOptions.deleteBuildPlans,
            this.programmingExerciseResetOptions.deleteStudentRepositories,
            this.programmingExerciseResetOptions.deleteStudentParticipationsSubmissionsAndResults,
        ];
        return (!options[0] && (options[1] || options[2])) || (!options[1] && options[2]);
    }

    /**
     * Resets the programming exercise with the given reset options
     */
    resetProgrammingExercise() {
        if (!this.programmingExercise.id) {
            return;
        }

        this.resetInProgress = true;
        this.programmingExerciseService.reset(this.programmingExercise.id, this.programmingExerciseResetOptions).subscribe({
            next: this.handleResetResponse,
            error: () => {
                this.resetInProgress = false;
            },
        });
    }
    /**
     * Handles the reset response by showing a success message, dismissing the active modal dialog, and resetting the resetInProgress flag.
     */
    handleResetResponse = () => {
        this.alertService.success('artemisApp.programmingExercise.reset.successMessage');
        this.activeModal.dismiss(true);
        this.resetInProgress = false;
    };

    /**
     * Check if all security checks are fulfilled
     * if the programmingExercise.title and entered confirmation matches
     * @returns {boolean} true if security checks are fulfilled, false otherwise
     */
    get areSecurityChecksFulfilled(): boolean {
        return this.confirmText === this.programmingExercise.title;
    }

    /**
     * Check if any reset options are selected
     * @returns {boolean} true if at least one reset option is selected, false otherwise
     */
    get hasSelectedOptions(): boolean {
        return (
            this.programmingExerciseResetOptions.deleteBuildPlans ||
            this.programmingExerciseResetOptions.deleteStudentRepositories ||
            this.programmingExerciseResetOptions.deleteStudentParticipationsSubmissionsAndResults ||
            this.programmingExerciseResetOptions.recreateBuildPlans
        );
    }
}
