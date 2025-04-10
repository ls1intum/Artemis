import { Component } from '@angular/core';
import { CourseLearnerProfileComponent } from 'app/core/user/settings/learner-profile/course-learner-profile.component';

@Component({
    selector: 'jhi-learner-profile',
    templateUrl: './learner-profile.component.html',
    styleUrls: ['../user-settings.scss'],
    imports: [CourseLearnerProfileComponent],
})
export class LearnerProfileComponent {}
