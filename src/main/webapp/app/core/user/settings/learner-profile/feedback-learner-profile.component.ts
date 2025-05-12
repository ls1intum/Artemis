import { Component, OnInit, inject, signal } from '@angular/core';
import { DoubleSliderComponent } from 'app/shared/double-slider/double-slider.component';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { LearnerProfileApiService } from 'app/learner-profile/service/learner-profile-api.service';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { LearnerProfileDTO } from 'app/learner-profile/shared/entities/learner-profile.model';

@Component({
    selector: 'jhi-feedback-learner-profile',
    templateUrl: './feedback-learner-profile.component.html',
    styleUrls: ['./feedback-learner-profile.component.scss'],
    imports: [DoubleSliderComponent, TranslateDirective, NgClass, ArtemisTranslatePipe, FaIconComponent, HelpIconComponent],
})
export class FeedbackLearnerProfileComponent implements OnInit {
    private alertService = inject(AlertService);
    private learnerProfileAPIService = inject(LearnerProfileApiService);
    protected translateService = inject(TranslateService);

    /**
     * Minimum value allowed for profile fields representing values on a Likert scale.
     * Must be the same as in the server in LearnerProfile.java
     */
    MIN_PROFILE_VALUE = 1;

    /**
     * Maximum value allowed for profile fields representing values on a Likert scale.
     * Must be the same as in the server in LearnerProfile.java
     */
    MAX_PROFILE_VALUE = 5;

    faSave = faSave;

    editing = false;
    profileId: number;
    feedbackAlternativeStandard = signal<number>(1);
    feedbackFollowupSummary = signal<number>(1);
    feedbackBriefDetailed = signal<number>(1);
    initialFeedbackAlternativeStandard = 1;
    initialFeedbackFollowupSummary = 1;
    initialFeedbackBriefDetailed = 1;

    async ngOnInit() {
        await this.loadProfile();
    }

    update() {
        const learnerProfile = new LearnerProfileDTO({
            id: this.profileId,
            feedbackAlternativeStandard: this.feedbackAlternativeStandard(),
            feedbackFollowupSummary: this.feedbackFollowupSummary(),
            feedbackBriefDetailed: this.feedbackBriefDetailed(),
        });

        // Try to update profile
        this.learnerProfileAPIService.putUpdatedLearnerProfile(learnerProfile).then(
            (updatedProfile) => {
                // update profile with response from server
                this.initialFeedbackAlternativeStandard = this.feedbackAlternativeStandard();
                this.initialFeedbackFollowupSummary = this.feedbackFollowupSummary();
                this.initialFeedbackBriefDetailed = this.feedbackBriefDetailed();

                this.editing = false;

                this.alertService.addAlert({
                    type: AlertType.SUCCESS,
                    message: 'Profile saved',
                    translationKey: 'artemisApp.learnerProfile.feedbackLearnerProfile.profileSaved',
                });
            },
            // Notify user of failure to update
            (error: HttpErrorResponse) => {
                const errorMessage = error.error ? error.error.title : error.headers?.get('x-artemisapp-alert');
                if (errorMessage) {
                    this.alertService.addAlert({
                        type: AlertType.DANGER,
                        message: errorMessage,
                        disableTranslation: true,
                    });
                }
            },
        );
    }

    async loadProfile() {
        const profile = await this.learnerProfileAPIService.getLearnerProfileForCurrentUser();
        this.profileId = profile.id;

        this.feedbackAlternativeStandard.set(profile.feedbackAlternativeStandard);
        this.initialFeedbackAlternativeStandard = profile.feedbackAlternativeStandard;

        this.feedbackFollowupSummary.set(profile.feedbackFollowupSummary);
        this.initialFeedbackFollowupSummary = profile.feedbackFollowupSummary;

        this.feedbackBriefDetailed.set(profile.feedbackBriefDetailed);
        this.initialFeedbackBriefDetailed = profile.feedbackBriefDetailed;
    }
}
