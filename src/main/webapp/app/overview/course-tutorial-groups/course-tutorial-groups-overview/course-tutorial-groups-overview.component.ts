import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Router } from '@angular/router';
import { Course, isMessagingEnabled } from 'app/entities/course.model';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';

@Component({
    selector: 'jhi-course-tutorial-groups-overview',
    templateUrl: './course-tutorial-groups-overview.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseTutorialGroupsOverviewComponent {
    @Input()
    course: Course;
    @Input()
    tutorialGroups: TutorialGroup[] = [];
    @Input()
    configuration?: TutorialGroupsConfiguration;

    readonly isMessagingEnabled = isMessagingEnabled;

    constructor(private router: Router) {}

    onTutorialGroupSelected = (tutorialGroup: TutorialGroup) => {
        this.router.navigate(['/courses', this.course.id!, 'tutorial-groups', tutorialGroup.id]);
    };
}
