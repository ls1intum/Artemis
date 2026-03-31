import { Component, input } from '@angular/core';
import { TutorialGroupDetailAccessLevel } from 'app/tutorialgroup/shared/tutorial-group-detail/tutorial-group-detail.component';
import { TutorialGroupDetailData } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

@Component({
    selector: 'jhi-tutorial-group-detail',
    template: '',
})
export class CourseTutorialGroupDetailStubComponent {
    courseId = input.required<number>();
    tutorialGroup = input.required<TutorialGroupDetailData>();
    isMessagingEnabled = input.required<boolean>();
    loggedInUserAccessLevel = input.required<TutorialGroupDetailAccessLevel>();
}
