import { Component, OnInit, inject } from '@angular/core';
import { CourseLearnerProfileComponent } from 'app/core/user/settings/learner-profile/course-learner-profile/course-learner-profile.component';
import { FeedbackLearnerProfileComponent } from 'app/core/user/settings/learner-profile/feedback-learner-profile/feedback-learner-profile.component';
import { LearnerProfileApiService } from 'app/core/user/settings/learner-profile/learner-profile-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { captureException } from '@sentry/angular';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [CourseLearnerProfileComponent, FeedbackLearnerProfileComponent],
})
export class LearnerProfileComponent implements OnInit {
    private readonly learnerProfileAPIService = inject(LearnerProfileApiService);
    private readonly alertService = inject(AlertService);

    // Gate rendering of course learner profiles until the base learner profile request has completed
    public coursePanelEnabled = false;

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
