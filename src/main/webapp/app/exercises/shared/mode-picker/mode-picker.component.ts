import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Exercise, ExerciseMode } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-mode-picker',
    templateUrl: './mode-picker.component.html',
    styles: ['div { cursor: pointer; }'],
})
export class ModePickerComponent {
    readonly INDIVIDUAL = ExerciseMode.INDIVIDUAL;
    readonly TEAM = ExerciseMode.TEAM;

    @Input() exercise: Exercise;
    @Output() ngModelChange = new EventEmitter<ExerciseMode>();

    /**
     * @function setMode
     * @desc set the mode and emit the changes to the parent component to notice changes
     * @param mode chosen exercise solving mode of type {ExerciseMode}
     */
    setMode(mode: ExerciseMode) {
        if (mode !== this.exercise.mode) {
            this.exercise.mode = mode;
            this.ngModelChange.emit(mode);
        }
    }
}
