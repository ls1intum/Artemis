import { Component, Input, Output, EventEmitter } from '@angular/core';
import { DifficultyLevel, Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-difficulty-picker',
    templateUrl: './difficulty-picker.component.html',
    styles: ['div { cursor: pointer; }']
})

export class DifficultyPickerComponent {
    readonly EASY = DifficultyLevel.EASY;
    readonly MEDIUM = DifficultyLevel.MEDIUM;
    readonly HARD = DifficultyLevel.HARD;

    @Input() exercise: Exercise;
    @Output() ngModelChange = new EventEmitter<string>();

    /**
     * @function setDifficulty
     * @desc set the difficulty tag and emit the changes to the parent component to notice changes
     * @param chosen level of difficulty {DifficultyLevel}
     */
    setDifficulty(level: DifficultyLevel) {
        this.exercise.difficulty = level;
        this.ngModelChange.emit(level);
    }
}
