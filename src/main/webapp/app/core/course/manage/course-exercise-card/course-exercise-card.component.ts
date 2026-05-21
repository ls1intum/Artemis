import { Component, input, signal } from '@angular/core';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    selector: 'jhi-course-exercise-card',
    templateUrl: './course-exercise-card.component.html',
    styleUrls: ['./course-exercise-card.component.scss', '../../../../quiz/shared/quiz.scss'],
    imports: [TranslateDirective, FaIconComponent, NgbCollapse],
})
export class CourseExerciseCardComponent {
    readonly headingJhiTranslate = input.required<string>();
    readonly leadingIcon = input<IconProp | undefined>(undefined);
    readonly exerciseCount = input.required<number>();
    readonly course = input.required<Course>();
    readonly isCollapsed = signal(false);

    // Icons
    faAngleRight = faAngleRight;
    faAngleDown = faAngleDown;
}
