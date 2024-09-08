import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseResetOptions, ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { faBan, faCircleNotch, faSpinner, faUndo } from '@fortawesome/free-solid-svg-icons';
import { PROFILE_AEOLUS, PROFILE_LOCALCI } from 'app/app.constants';

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
    hasCustomizedBuildPlans = false;

    // Icons
    faBan = faBan;
    faCircleNotch = faCircleNotch;
    faSpinner = faSpinner;
    faUndo = faUndo;

    constructor(
        private alertService: AlertService,
        private profileService: ProfileService,
        private programmingExerciseService: ProgrammingExerciseService,
        public activeModal: NgbActiveModal,
    ) {}

    ngOnInit() {
        this.isLoading = true;
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.versionControlName = profileInfo.versionControlName;
                this.continuousIntegrationName = profileInfo.continuousIntegrationName;
                this.hasCustomizedBuildPlans = profileInfo?.activeProfiles.includes(PROFILE_LOCALCI) || profileInfo?.activeProfiles.includes(PROFILE_AEOLUS);
            }
        });
        this.resetInProgress = false;
        this.programmingExerciseResetOptions = {
            deleteBuildPlans: false,
            deleteRepositories: false,
            deleteParticipationsSubmissionsAndResults: false,
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
                this.alertService.error('artemisApp.programmingExercise.reset.errorMessage');
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
     * Check if all security checks are fulfilled and the user can submit the reset
     * @returns {boolean} true if the user can submit, false otherwise
     */
    get canSubmit(): boolean {
        return this.confirmText === this.programmingExercise.title && this.hasSelectedOptions && !this.resetInProgress;
    }

    /**
     * Check if any reset options are selected
     * @returns {boolean} true if at least one reset option is selected, false otherwise
     */
    get hasSelectedOptions(): boolean {
        return (
            this.programmingExerciseResetOptions.deleteBuildPlans ||
            this.programmingExerciseResetOptions.deleteRepositories ||
            this.programmingExerciseResetOptions.deleteParticipationsSubmissionsAndResults ||
            this.programmingExerciseResetOptions.recreateBuildPlans
        );
    }
}
