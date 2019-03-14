import { Component, Input } from '@angular/core';
import { DifficultyLevel, Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-difficulty-picker',
    templateUrl: './difficulty-picker.component.html'
})

export class DifficultyPickerComponent {
    readonly EASY = DifficultyLevel.EASY;
    readonly MEDIUM = DifficultyLevel.MEDIUM;
    readonly HARD = DifficultyLevel.HARD;

    @Input() exercise: Exercise;
}
