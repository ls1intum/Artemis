import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { LearnerProfileApiService } from 'app/core/user/settings/learner-profile/learner-profile-api.service';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { LearnerProfileDTO } from 'app/core/user/settings/learner-profile/dto/learner-profile-dto.model';
import { SegmentedToggleComponent } from 'app/shared/segmented-toggle/segmented-toggle.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FeedbackOnboardingModalComponent } from 'app/core/user/settings/learner-profile/feedback-learner-profile/onboarding-modal/feedback-onboarding-modal.component';

@Component({
    selector: 'jhi-feedback-learner-profile',
    templateUrl: './feedback-learner-profile.component.html',
    styleUrls: ['../learner-profile.component.scss'],
    imports: [TranslateDirective, NgClass, SegmentedToggleComponent],
})
export class FeedbackLearnerProfileComponent implements OnInit {
    private alertService = inject(AlertService);
    private learnerProfileAPIService = inject(LearnerProfileApiService);
    private modalService = inject(NgbModal);
    protected translateService = inject(TranslateService);

    /** Signal containing the learner profile for the current user */
    public readonly learnerProfile = signal<LearnerProfileDTO | undefined>(undefined);

    /** Computed signal indicating whether the profile is set up */
    public readonly isProfileSetup = computed(() => {
        const profile = this.learnerProfile();
        return !!profile && profile.hasSetupFeedbackPreferences === true;
    });

    /** Flag indicating whether the profile editing is disabled */
    disabled = true;

    openOnboardingModal() {
        const modalRef = this.modalService.open(FeedbackOnboardingModalComponent, { size: 'lg' });
        modalRef.result.then(() => {
            this.loadProfile();
        });
    }

    /**
     * Options mapped from shared options with translated labels.
     */
    protected readonly feedbackDetailOptions = [
        { label: this.translateService.instant('artemisApp.learnerProfile.feedbackLearnerProfile.feedbackDetail.brief'), value: 1 },
        { label: this.translateService.instant('artemisApp.learnerProfile.feedbackLearnerProfile.feedbackDetail.neutral'), value: 2 },
        { label: this.translateService.instant('artemisApp.learnerProfile.feedbackLearnerProfile.feedbackDetail.detailed'), value: 3 },
    ];
    protected readonly feedbackFormalityOptions = [
        { label: this.translateService.instant('artemisApp.learnerProfile.feedbackLearnerProfile.feedbackFormality.formal'), value: 1 },
        { label: this.translateService.instant('artemisApp.learnerProfile.feedbackLearnerProfile.feedbackFormality.neutral'), value: 2 },
        { label: this.translateService.instant('artemisApp.learnerProfile.feedbackLearnerProfile.feedbackFormality.friendly'), value: 3 },
    ];

    /** Signals for learner profile settings */
    feedbackDetail = signal<number | undefined>(undefined);
    feedbackFormality = signal<number | undefined>(undefined);

    /** Icon for save button */
    protected readonly faSave = faSave;

    async ngOnInit(): Promise<void> {
        await this.loadProfile();
    }

    /**
     * Loads the learner profile for the current user.
     * Handles any errors that occur during the loading process.
     */
    private async loadProfile(): Promise<void> {
        try {
            const profile = await this.learnerProfileAPIService.getLearnerProfileForCurrentUser();
            this.learnerProfile.set(profile);
            this.disabled = false;
            this.updateProfileValues(profile);
        } catch (error) {
            if (error instanceof HttpErrorResponse && error.status === 404) {
                this.disabled = true;
                this.learnerProfile.set(undefined);
            } else {
                this.handleError(error);
            }
        }
    }

    /**
     * Updates the profile values in the component's signals.
     * @param learnerProfile - The learner profile containing the values to update
     */
    private updateProfileValues(learnerProfile: LearnerProfileDTO): void {
        this.feedbackDetail.set(learnerProfile.feedbackDetail ?? undefined);
        this.feedbackFormality.set(learnerProfile.feedbackFormality ?? undefined);
    }

    /**
     * Handles toggle change events for profile settings.
     * Updates the profile in the server when any setting is changed.
     */
    async onToggleChange(): Promise<void> {
        const profile = this.learnerProfile();
        if (!profile) {
            return;
        }

        const updatedProfile = new LearnerProfileDTO({
            id: profile.id,
            feedbackDetail: this.feedbackDetail(),
            feedbackFormality: this.feedbackFormality(),
        });

        try {
            const result = await this.learnerProfileAPIService.putUpdatedLearnerProfile(updatedProfile);
            this.learnerProfile.set(result);
            this.alertService.closeAll();
            this.alertService.addAlert({
                type: AlertType.SUCCESS,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.profileSaved',
            });
        } catch (error) {
            this.handleError(error);
        }
    }

    /**
     * Handles errors that occur during API calls.
     * @param error - The error that occurred
     */
    private handleError(error: unknown): void {
        if (error instanceof HttpErrorResponse) {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: error.message,
            });
        } else {
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: 'artemisApp.learnerProfile.feedbackLearnerProfile.error',
            });
        }
    }
}
