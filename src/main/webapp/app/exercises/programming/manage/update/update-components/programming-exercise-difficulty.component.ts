import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseCreationConfig } from 'app/exercises/programming/manage/update/wizard-mode/programming-exercise-update-wizard.component';

@Component({
    selector: 'jhi-programming-exercise-difficulty',
    templateUrl: './programming-exercise-difficulty.component.html',
    styleUrls: ['../../programming-exercise-form.scss'],
})
export class ProgrammingExerciseDifficultyComponent {
    @Input() programmingExercise: ProgrammingExercise;
    @Input() programmingExerciseCreationConfig: ProgrammingExerciseCreationConfig;
}
