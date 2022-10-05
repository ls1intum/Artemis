import { Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';

@Component({
    selector: 'jhi-course-tutorial-groups-registered',
    templateUrl: './course-tutorial-groups-registered.component.html',
})
export class CourseTutorialGroupsRegisteredComponent {
    @Input()
    registeredTutorialGroups: TutorialGroup[] = [];
    @Input()
    courseId: number;
}
