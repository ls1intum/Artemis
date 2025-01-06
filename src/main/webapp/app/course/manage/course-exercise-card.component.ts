import { Component, Input } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-course-exercise-card',
    templateUrl: './course-exercise-card.component.html',
    styleUrls: ['./course-exercise-card.component.scss', '../../exercises/quiz/shared/quiz.scss'],
    imports: [TranslateDirective, FaIconComponent, NgbCollapse],
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
