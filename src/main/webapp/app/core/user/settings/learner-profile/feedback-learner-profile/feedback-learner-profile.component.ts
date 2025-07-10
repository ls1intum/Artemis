import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { LearnerProfileApiService } from 'app/core/user/settings/learner-profile/learner-profile-api.service';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { LearnerProfileDTO } from 'app/core/user/settings/learner-profile/dto/learner-profile-dto.model';
import { SegmentedToggleComponent } from 'app/shared/segmented-toggle/segmented-toggle.component';
import { ALTERNATIVE_STANDARD_OPTIONS, BRIEF_DETAILED_OPTIONS, FOLLOWUP_SUMMARY_OPTIONS } from 'app/core/user/settings/learner-profile/entities/learner-profile-options.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FeedbackOnboardingModalComponent } from './feedback-onboarding-modal.component';

@Component({
    selector: 'jhi-feedback-learner-profile',
    templateUrl: './feedback-learner-profile.component.html',
    styleUrls: ['../learner-profile.component.scss'],
    imports: [TranslateDirective, NgClass, ArtemisTranslatePipe, FaIconComponent, HelpIconComponent, SegmentedToggleComponent],
})
export class FeedbackLearnerProfileComponent implements OnInit {
    private alertService = inject(AlertService);
    private learnerProfileAPIService = inject(LearnerProfileApiService);
    protected translateService = inject(TranslateService);

    /** Signal containing the learner profile for the current user */
    public readonly learnerProfile = signal<LearnerProfileDTO | undefined>(undefined);

    /** Flag indicating whether the profile editing is disabled */
    disabled = true;

    profileMissing = false;

    constructor(private modalService: NgbModal) {}

    openOnboardingModal() {
        const modalRef = this.modalService.open(FeedbackOnboardingModalComponent, { size: 'lg' });
        modalRef.componentInstance.profileMissing = this.profileMissing;
    }

    get isProfileSetup(): boolean {
        const profile = this.learnerProfile();
        return !this.profileMissing && !!profile && !!profile.hasSetupFeedbackPreferences;
    }

    /**
     * Options mapped from shared options with translated labels.
     */
    protected readonly feedbackAlternativeStandardOptions = ALTERNATIVE_STANDARD_OPTIONS.map((option) => ({
        label: this.translateService.instant(option.translationKey),
        value: option.level,
    }));
    protected readonly feedbackFollowupSummaryOptions = FOLLOWUP_SUMMARY_OPTIONS.map((option) => ({
        label: this.translateService.instant(option.translationKey),
        value: option.level,
    }));
    protected readonly feedbackBriefDetailedOptions = BRIEF_DETAILED_OPTIONS.map((option) => ({
        label: this.translateService.instant(option.translationKey),
        value: option.level,
    }));

    /** Signals for learner profile settings */
    feedbackAlternativeStandard = signal<number | undefined>(undefined);
    feedbackFollowupSummary = signal<number | undefined>(undefined);
    feedbackBriefDetailed = signal<number | undefined>(undefined);

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
            this.profileMissing = false;
            this.updateProfileValues(profile);
        } catch (error) {
            if (error instanceof HttpErrorResponse && error.status === 404) {
                this.profileMissing = true;
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
        this.feedbackAlternativeStandard.set(
            learnerProfile.feedbackAlternativeStandard !== undefined && learnerProfile.feedbackAlternativeStandard !== null && learnerProfile.feedbackAlternativeStandard !== 0
                ? learnerProfile.feedbackAlternativeStandard
                : undefined,
        );
        this.feedbackFollowupSummary.set(
            learnerProfile.feedbackFollowupSummary !== undefined && learnerProfile.feedbackFollowupSummary !== null && learnerProfile.feedbackFollowupSummary !== 0
                ? learnerProfile.feedbackFollowupSummary
                : undefined,
        );
        this.feedbackBriefDetailed.set(
            learnerProfile.feedbackBriefDetailed !== undefined && learnerProfile.feedbackBriefDetailed !== null && learnerProfile.feedbackBriefDetailed !== 0
                ? learnerProfile.feedbackBriefDetailed
                : undefined,
        );
    }

    /**
     * Handles toggle change events for profile settings.
     * Updates the profile in the backend when any setting is changed.
     */
    async onToggleChange(): Promise<void> {
        const profile = this.learnerProfile();
        if (!profile) {
            return;
        }

        const updatedProfile = new LearnerProfileDTO({
            id: profile.id,
            feedbackAlternativeStandard: this.feedbackAlternativeStandard(),
            feedbackFollowupSummary: this.feedbackFollowupSummary(),
            feedbackBriefDetailed: this.feedbackBriefDetailed(),
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
