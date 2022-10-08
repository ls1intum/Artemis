import { Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({
    selector: 'jhi-tutorial-groups-course-information',
    templateUrl: './tutorial-groups-course-information.component.html',
})
export class TutorialGroupsCourseInformationComponent {
    @Input()
    tutorialGroups: TutorialGroup[] = [];

    get totalNumberOfRegistrations(): number {
        return this.tutorialGroups.reduce((acc, tutorialGroup) => acc + (tutorialGroup.numberOfRegisteredUsers ?? 0), 0);
    }
}
