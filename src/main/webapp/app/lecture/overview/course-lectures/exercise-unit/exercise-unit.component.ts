import { Component, ViewEncapsulation, input } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { CourseExerciseRowComponent } from 'app/core/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
@Component({
    selector: 'jhi-exercise-unit',
    templateUrl: './exercise-unit.component.html',
    encapsulation: ViewEncapsulation.None,
    styleUrls: ['./exercise-unit.component.scss'],
    imports: [CourseExerciseRowComponent],
    host: { class: 'exercise-unit' },
})
export class ExerciseUnitComponent {
    exerciseUnit = input.required<ExerciseUnit>();
    course = input.required<Course>();
    isPresentationMode = input<boolean>(false);
}
