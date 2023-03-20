import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exercise-categories',
    templateUrl: './exercise-categories.component.html',
})
export class ExerciseCategoriesComponent {
    @Input() exercise: Exercise;
}
