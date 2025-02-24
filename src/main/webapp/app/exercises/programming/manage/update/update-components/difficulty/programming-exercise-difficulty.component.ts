import { Component, input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DifficultyPickerComponent } from 'app/exercises/shared/difficulty-picker/difficulty-picker.component';

@Component({
    selector: 'jhi-programming-exercise-difficulty',
    templateUrl: './programming-exercise-difficulty.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
    imports: [TranslateDirective, DifficultyPickerComponent],
})
export class ProgrammingExerciseDifficultyComponent {
    programmingExercise = input.required<ProgrammingExercise>();
}
