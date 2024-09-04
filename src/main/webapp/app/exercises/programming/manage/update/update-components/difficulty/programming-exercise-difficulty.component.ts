import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ArtemisDifficultyPickerModule } from 'app/exercises/shared/difficulty-picker/difficulty-picker.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-programming-exercise-difficulty',
    standalone: true,
    templateUrl: './programming-exercise-difficulty.component.html',
    styleUrls: ['../../../programming-exercise-form.scss'],
    imports: [ArtemisDifficultyPickerModule, ArtemisSharedCommonModule],
})
export class ProgrammingExerciseDifficultyComponent {
    @Input({ required: true }) programmingExercise: ProgrammingExercise;
}
