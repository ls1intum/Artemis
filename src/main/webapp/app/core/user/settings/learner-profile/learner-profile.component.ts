import { Component } from '@angular/core';
import { CourseLearnerProfileComponent } from 'app/core/user/settings/learner-profile/course-learner-profile/course-learner-profile.component';
import { FeedbackLearnerProfileComponent } from 'app/core/user/settings/learner-profile/feedback-learner-profile/feedback-learner-profile.component';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [CourseLearnerProfileComponent, FeedbackLearnerProfileComponent],
})
export class LearnerProfileComponent {}
