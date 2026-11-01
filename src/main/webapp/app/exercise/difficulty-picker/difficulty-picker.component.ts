import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { Component, input, output } from '@angular/core';
import { DifficultyLevel, Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-difficulty-picker',
    templateUrl: './difficulty-picker.component.html',
    imports: [TumAetUiButtonDirective, TranslateDirective],
})
export class DifficultyPickerComponent {
    readonly DifficultyLevel = DifficultyLevel;

    readonly exercise = input<Exercise>(undefined!);
    readonly ngModelChange = output();

    /**
     * Sets the difficulty level of an exercise and emits the changes to the parent component to notice changes
     * @param level chosen level of difficulty {DifficultyLevel}
     */
    setDifficulty(level?: DifficultyLevel) {
        this.exercise().difficulty = level;
        this.ngModelChange.emit();
    }
}
