import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CourseLearnerProfileComponent } from 'app/account/user/settings/learner-profile/course-learner-profile/course-learner-profile.component';
import { FeedbackLearnerProfileComponent } from 'app/account/user/settings/learner-profile/feedback-learner-profile/feedback-learner-profile.component';
import { LearnerProfileApiService } from 'app/account/user/settings/learner-profile/learner-profile-api.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { captureException } from '@sentry/angular';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { InsightsLearnerProfileComponent } from 'app/account/user/settings/learner-profile/insights-learner-profile/insights-learner-profile.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { insightsSectionAvailable, isAtlasModuleActive, isIrisModuleActive } from 'app/account/user/settings/learner-profile/learner-profile-availability';

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
    private readonly profileService = inject(ProfileService);

    // Each section is only rendered — and the atlas request only made — while the module behind it is active.
    // Otherwise the requests answer 404 and the page reports the failures as errors. LearnerProfileGuard keeps the
    // route reachable only while at least one section can render.
    public readonly atlasEnabled = signal(false);
    private readonly irisEnabled = signal(false);
    private readonly memirisEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.Memiris), { requireSync: true });
    public readonly insightsEnabled = computed(() => insightsSectionAvailable(this.irisEnabled(), this.memirisEnabled()));

    // Gate rendering of course learner profiles until the base learner profile request has completed
    public readonly coursePanelEnabled = signal(false);

    async ngOnInit(): Promise<void> {
        this.atlasEnabled.set(isAtlasModuleActive(this.profileService));
        this.irisEnabled.set(isIrisModuleActive(this.profileService));
        if (!this.atlasEnabled()) {
            return;
        }
        try {
            await this.learnerProfileAPIService.getLearnerProfileForCurrentUser();
        } catch (error) {
            captureException(error);
            this.alertService.info('artemisApp.learnerProfile.loadFailed');
        } finally {
            this.coursePanelEnabled.set(true);
        }
    }
}
