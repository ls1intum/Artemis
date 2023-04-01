import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-programming-exercise-difficulty',
    templateUrl: './programming-exercise-difficulty.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseDifficultyComponent {
    @Input() isImport: boolean;
    @Input() programmingExercise: ProgrammingExercise;
}
