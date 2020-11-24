import { Component, Input, ViewEncapsulation } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';

@Component({
    selector: 'jhi-exercise-unit',
    templateUrl: './exercise-unit.component.html',
    encapsulation: ViewEncapsulation.None,
    styleUrls: ['./exercise-unit.component.scss'],
})
export class ExerciseUnitComponent {
    @Input()
    exerciseUnit: ExerciseUnit;

    @Input()
    course: Course;

    @Input()
    isPresentationMode: false;
}
