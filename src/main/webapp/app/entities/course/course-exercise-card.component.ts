import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-course-exercise-card',
    templateUrl: './course-exercise-card.component.html',
    styles: []
})
export class CourseExerciseCardComponent {
    @Input() headingJhiTranslate: string;
    @Input() exerciseRouterLink: string[];
    isCollapsed = false;
}
