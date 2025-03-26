import { Component, HostBinding, Input, ViewEncapsulation } from '@angular/core';
import { Course } from 'app/core/shared/entities/course.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { CourseExerciseRowComponent } from 'app/core/course/overview/course-exercises/course-exercise-row.component';
@Component({
    selector: 'jhi-exercise-unit',
    templateUrl: './exercise-unit.component.html',
    encapsulation: ViewEncapsulation.None,
    styleUrls: ['./exercise-unit.component.scss'],
    imports: [CourseExerciseRowComponent],
})
export class ExerciseUnitComponent {
    @HostBinding('className') componentClass: string;

    constructor() {
        this.componentClass = 'exercise-unit';
    }

    @Input() exerciseUnit: ExerciseUnit;
    @Input() course: Course;
    @Input() isPresentationMode = false;
}
