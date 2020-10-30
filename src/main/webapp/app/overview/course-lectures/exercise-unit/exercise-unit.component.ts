import { Component, Input } from '@angular/core';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';

@Component({
    selector: 'jhi-exercise-unit',
    templateUrl: './exercise-unit.component.html',
    styles: [],
})
export class ExerciseUnitComponent {
    @Input()
    exerciseUnit: ExerciseUnit;

    @Input()
    isPresentationMode: false;
}
