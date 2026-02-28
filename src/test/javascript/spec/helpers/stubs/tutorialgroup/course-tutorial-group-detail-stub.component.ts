import { Component, input } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TutorialGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

@Component({
    selector: 'jhi-course-tutorial-group-detail',
    template: '',
})
export class CourseTutorialGroupDetailStubComponent {
    course = input.required<Course>();
    tutorialGroup = input.required<TutorialGroupDTO>();
}
