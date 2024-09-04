import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';

@Component({
    selector: 'jhi-programming-exercise-difficulty',
    standalone: true,
    templateUrl: './programming-exercise-difficulty.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
    imports: [ArtemisDifficultyPickerModule],
})
export class ProgrammingExerciseDifficultyComponent {
    @Input({ required: true }) programmingExercise: ProgrammingExercise;

    @Output() triggerValidation = new EventEmitter<void>();
}
