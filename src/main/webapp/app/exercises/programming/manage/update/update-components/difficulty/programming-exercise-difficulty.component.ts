import { Component, input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-programming-exercise-difficulty',
    standalone: true,
    templateUrl: './programming-exercise-difficulty.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
    imports: [ArtemisDifficultyPickerModule, TranslateDirective],
})
export class ProgrammingExerciseDifficultyComponent {
    programmingExercise = input.required<ProgrammingExercise>();
}
