import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { Course } from 'app/entities/course.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';

@Component({
    selector: 'jhi-course-tutorial-groups-registered',
    templateUrl: './course-tutorial-groups-registered.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseTutorialGroupsRegisteredComponent {
    @Input()
    registeredTutorialGroups: TutorialGroup[] = [];
    @Input()
    course: Course;

    @Input()
    configuration?: TutorialGroupsConfiguration;
}
