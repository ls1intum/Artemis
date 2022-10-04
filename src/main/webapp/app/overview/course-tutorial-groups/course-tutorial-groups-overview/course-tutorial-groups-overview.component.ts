import { Component, Input } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-course-tutorial-groups-overview',
    templateUrl: './course-tutorial-groups-overview.component.html',
    styleUrls: ['./course-tutorial-groups-overview.component.scss'],
})
export class CourseTutorialGroupsOverviewComponent {
    @Input()
    courseId: number;
    @Input()
    tutorialGroups: TutorialGroup[] = [];

    get totalNumberOfRegistrations(): number {
        return this.tutorialGroups.reduce((acc, tutorialGroup) => acc + (tutorialGroup.numberOfRegisteredUsers ?? 0), 0);
    }

    constructor(private router: Router) {}

    onTutorialGroupSelected = (tutorialGroup: TutorialGroup) => {
        this.router.navigate(['/courses', this.courseId, 'tutorial-groups', tutorialGroup.id]);
    };
}
