import { Component, Input, OnInit, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseResetOptions, ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { faBan, faCircleNotch, faSpinner, faUndo } from '@fortawesome/free-solid-svg-icons';
import { PROFILE_AEOLUS, PROFILE_LOCALCI } from 'app/app.constants';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-programming-exercise-reset-dialog',
    templateUrl: './programming-exercise-reset-dialog.component.html',
    styles: ['textarea { width: 100%; }'],
    imports: [FormsModule, TranslateDirective, FaIconComponent],
})
export class ProgrammingExerciseResetDialogComponent implements OnInit {
    private alertService = inject(AlertService);
    private profileService = inject(ProfileService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    activeModal = inject(NgbActiveModal);

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

    ngOnInit() {
        this.isLoading = true;
        const profileInfo = this.profileService.getProfileInfo();
        this.versionControlName = profileInfo.versionControlName;
        this.continuousIntegrationName = profileInfo.continuousIntegrationName;
        this.hasCustomizedBuildPlans = this.profileService.isProfileActive(PROFILE_LOCALCI) || this.profileService.isProfileActive(PROFILE_AEOLUS);

        this.resetInProgress = false;
        this.programmingExerciseResetOptions = {
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
        return this.programmingExerciseResetOptions.deleteParticipationsSubmissionsAndResults || this.programmingExerciseResetOptions.recreateBuildPlans;
    }
}
