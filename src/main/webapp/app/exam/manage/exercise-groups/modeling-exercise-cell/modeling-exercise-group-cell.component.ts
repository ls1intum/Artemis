import { Component, Input } from '@angular/core';
import { ExerciseType } from 'app/entities/exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

@Component({
    selector: 'jhi-modeling-exercise-group-cell',
    templateUrl: './modeling-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
})
export class ModelingExerciseGroupCellComponent {
    exerciseType = ExerciseType;

    @Input()
    exercise: ModelingExercise;
}
