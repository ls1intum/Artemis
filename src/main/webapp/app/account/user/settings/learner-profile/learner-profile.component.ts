import { Component, OnInit, inject, signal } from '@angular/core';
import { CourseLearnerProfileComponent } from 'app/account/user/settings/learner-profile/course-learner-profile/course-learner-profile.component';
import { FeedbackLearnerProfileComponent } from 'app/account/user/settings/learner-profile/feedback-learner-profile/feedback-learner-profile.component';
import { LearnerProfileApiService } from 'app/account/user/settings/learner-profile/learner-profile-api.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { captureException } from 'app/core/error-reporting/capture-exception';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { isAtlasModuleActive } from 'app/account/user/settings/learner-profile/learner-profile-availability';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [CourseLearnerProfileComponent, FeedbackLearnerProfileComponent],
})
export class LearnerProfileComponent implements OnInit {
    private readonly learnerProfileAPIService = inject(LearnerProfileApiService);
    private readonly alertService = inject(AlertService);
    private readonly profileService = inject(ProfileService);

    // The sections are only rendered — and the atlas request only made — while the atlas module is active.
    // Otherwise the requests answer 404 and the page reports the failures as errors. LearnerProfileGuard keeps the
    // route reachable only while the sections can render.
    public readonly atlasEnabled = signal(false);

    // Gate rendering of course learner profiles until the base learner profile request has completed
    public readonly coursePanelEnabled = signal(false);

    ngOnInit(): void {
        void this.initializeLearnerProfileComponent();
    }

    private async initializeLearnerProfileComponent(): Promise<void> {
        this.atlasEnabled.set(isAtlasModuleActive(this.profileService));
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
