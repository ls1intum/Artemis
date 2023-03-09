import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';
import { Course } from 'app/entities/course.model';

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
