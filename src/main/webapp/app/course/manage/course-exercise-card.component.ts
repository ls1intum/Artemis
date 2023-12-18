import { Component, Input } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import { ExerciseType, getIcon } from 'app/entities/exercise.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-course-exercise-card',
    templateUrl: './course-exercise-card.component.html',
    styleUrls: ['./course-exercise-card.component.scss', '../../exercises/quiz/shared/quiz.scss'],
})
export class CourseExerciseCardComponent {
    @Input() headingJhiTranslate: string;
    @Input() exerciseCount: number;
    @Input() course: Course;
    @Input() exerciseType: ExerciseType;
    isCollapsed = false;

    // Icons
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;

    get exerciseIcon(): IconProp {
        return getIcon(this.exerciseType);
    }
}
