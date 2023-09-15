import { Component, Input } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-exercise-card',
    templateUrl: './course-exercise-card.component.html',
    styleUrls: ['./course-exercise-card.component.scss', '../../exercises/quiz/shared/quiz.scss'],
})
export class CourseExerciseCardComponent {
    @Input() headingJhiTranslate: string;
    @Input() exerciseCount: number;
    @Input() course: Course;
    isCollapsed = false;

    // Icons
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;
}
