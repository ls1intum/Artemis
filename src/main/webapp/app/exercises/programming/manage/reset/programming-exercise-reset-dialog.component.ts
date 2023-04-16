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

    clear() {
        this.activeModal.dismiss('cancel');
    }

    showUndeletedArtifactsWarning() {
        const options = [
            this.programmingExerciseResetOptions.deleteBuildPlans,
            this.programmingExerciseResetOptions.deleteStudentRepositories,
            this.programmingExerciseResetOptions.deleteStudentParticipationsSubmissionsAndResults,
        ];
        return (!options[0] && (options[1] || options[2])) || (!options[1] && options[2]);
    }

    resetProgrammingExercise() {
        if (!this.programmingExercise.id) {
            return;
        }

        this.resetInProgress = true;
        this.programmingExerciseService.reset(this.programmingExercise.id, this.programmingExerciseResetOptions).subscribe({
            next: () => this.handleResetResponse,
            error: () => {
                this.resetInProgress = false;
            },
        });
    }

    handleResetResponse = () => {
        this.alertService.success('artemisApp.programmingExercise.reset.successMessage');
        this.activeModal.dismiss(true);
        this.resetInProgress = false;
    };

    /**
     * Check if all security checks are fulfilled
     * if the programmingExercise.title and entered confirmation matches
     */
    get areSecurityChecksFulfilled(): boolean {
        return this.confirmText === this.programmingExercise.title;
    }

    get hasSelectedOptions(): boolean {
        return (
            this.programmingExerciseResetOptions.deleteBuildPlans ||
            this.programmingExerciseResetOptions.deleteStudentRepositories ||
            this.programmingExerciseResetOptions.deleteStudentParticipationsSubmissionsAndResults ||
            this.programmingExerciseResetOptions.recreateBuildPlans
        );
    }
}
