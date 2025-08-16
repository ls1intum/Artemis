import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { CourseLearnerProfileComponent } from 'app/core/user/settings/learner-profile/course-learner-profile/course-learner-profile.component';
import { FeedbackLearnerProfileComponent } from 'app/core/user/settings/learner-profile/feedback-learner-profile/feedback-learner-profile.component';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { InsightsLearnerProfileComponent } from 'app/core/user/settings/learner-profile/insights-learner-profile/insights-learner-profile.component';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [CourseLearnerProfileComponent, FeedbackLearnerProfileComponent, InsightsLearnerProfileComponent],
})
export class LearnerProfileComponent {
    private readonly featureToggleService = inject(FeatureToggleService);

    memirisEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.Memiris), { requireSync: true });
}
