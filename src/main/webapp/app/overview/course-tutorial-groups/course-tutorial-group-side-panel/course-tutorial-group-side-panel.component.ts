import { Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({
    selector: 'jhi-course-tutorial-group-side-panel',
    templateUrl: './course-tutorial-group-side-panel.component.html',
})
export class CourseTutorialGroupSidePanelComponent {
    @Input()
    tutorialGroups: TutorialGroup[] = [];

    get totalNumberOfRegistrations(): number {
        return this.tutorialGroups.reduce((acc, tutorialGroup) => acc + (tutorialGroup.numberOfRegisteredUsers ?? 0), 0);
    }
}
