import { Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Router } from '@angular/router';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-tutorial-groups-overview',
    templateUrl: './course-tutorial-groups-overview.component.html',
})
export class CourseTutorialGroupsOverviewComponent {
    @Input()
    course: Course;
    @Input()
    tutorialGroups: TutorialGroup[] = [];

    constructor(private router: Router) {}

    onTutorialGroupSelected = (tutorialGroup: TutorialGroup) => {
        this.router.navigate(['/courses', this.course.id!, 'tutorial-groups', tutorialGroup.id]);
    };
}
