import { Component, Input } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-course-exercise-card',
    templateUrl: './course-exercise-card.component.html',
    styleUrls: ['./course-exercise-card.component.scss', '../../exercises/quiz/shared/quiz.scss'],
})
export class CourseExerciseCardComponent {
    @Input() headingJhiTranslate: string;
    isCollapsed = false;

    // Icons
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;
}
