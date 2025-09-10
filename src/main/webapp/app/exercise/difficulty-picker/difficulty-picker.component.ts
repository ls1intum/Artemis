import { Component, EventEmitter, Input, Output } from '@angular/core';
import { DifficultyLevel, Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-difficulty-picker',
    templateUrl: './difficulty-picker.component.html',
    styles: ['div { cursor: pointer; }'],
    imports: [TranslateDirective, NgClass],
})
export class DifficultyPickerComponent {
    readonly DifficultyLevel = DifficultyLevel;

    @Input() exercise: Exercise;
    @Output() ngModelChange = new EventEmitter();

    /**
     * Sets the difficulty level of an exercise and emits the changes to the parent component to notice changes
     * @param level chosen level of difficulty {DifficultyLevel}
     */
    setDifficulty(level?: DifficultyLevel) {
        this.exercise.difficulty = level;
        this.ngModelChange.emit();
    }
}
