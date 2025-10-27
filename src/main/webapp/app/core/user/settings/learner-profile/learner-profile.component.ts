import { Component, OnInit, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CourseLearnerProfileComponent } from 'app/core/user/settings/learner-profile/course-learner-profile/course-learner-profile.component';
import { FeedbackLearnerProfileComponent } from 'app/core/user/settings/learner-profile/feedback-learner-profile/feedback-learner-profile.component';
import { LearnerProfileApiService } from 'app/core/user/settings/learner-profile/learner-profile-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { captureException } from '@sentry/angular';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { InsightsLearnerProfileComponent } from 'app/core/user/settings/learner-profile/insights-learner-profile/insights-learner-profile.component';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [CourseLearnerProfileComponent, FeedbackLearnerProfileComponent, InsightsLearnerProfileComponent],
})
export class LearnerProfileComponent implements OnInit {
    private readonly learnerProfileAPIService = inject(LearnerProfileApiService);
    private readonly alertService = inject(AlertService);
    private readonly featureToggleService = inject(FeatureToggleService);

    // Gate rendering of course learner profiles until the base learner profile request has completed
    public coursePanelEnabled = false;
    memirisEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.Memiris), { requireSync: true });

    async ngOnInit(): Promise<void> {
        try {
            await this.learnerProfileAPIService.getLearnerProfileForCurrentUser();
        } catch (error) {
            captureException(error);
            this.alertService.info('artemisApp.learnerProfile.loadFailed');
        } finally {
            this.coursePanelEnabled = true;
        }
    }
}
