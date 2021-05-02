import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-course-exercise-card',
    templateUrl: './course-exercise-card.component.html',
    styleUrls: ['./course-exercise-card.component.scss', '../../exercises/quiz/shared/quiz.scss'],
})
export class CourseExerciseCardComponent {
    @Input() headingJhiTranslate: string;
    isCollapsed = false;
}
