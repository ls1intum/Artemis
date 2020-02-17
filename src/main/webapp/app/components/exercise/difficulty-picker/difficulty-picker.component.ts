import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DifficultyLevel, Exercise } from 'app/entities/exercise/exercise.model';

@Component({
    selector: 'jhi-difficulty-picker',
    templateUrl: './difficulty-picker.component.html',
    styles: ['div { cursor: pointer; }'],
})
export class DifficultyPickerComponent {
    readonly EASY = DifficultyLevel.EASY;
    readonly MEDIUM = DifficultyLevel.MEDIUM;
    readonly HARD = DifficultyLevel.HARD;

    @Input() exercise: Exercise;
    @Output() ngModelChange = new EventEmitter();

    /**
     * @function setDifficulty
     * @desc set the difficulty tag and emit the changes to the parent component to notice changes
     * @param level chosen level of difficulty {DifficultyLevel}
     */
    setDifficulty(level: DifficultyLevel | null) {
        this.exercise.difficulty = level;
        this.ngModelChange.emit();
    }
}
