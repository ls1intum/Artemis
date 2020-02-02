import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Exercise, ExerciseMode } from 'app/entities/exercise';
import { TeamAssignmentConfig } from 'app/entities/team-assignment-config/team-assignment-config.model';

@Component({
    selector: 'jhi-mode-picker',
    templateUrl: './mode-picker.component.html',
    styles: ['div { cursor: pointer; }'],
})
export class ModePickerComponent {
    readonly INDIVIDUAL = ExerciseMode.INDIVIDUAL;
    readonly TEAM = ExerciseMode.TEAM;

    @Input() exercise: Exercise;
    @Output() ngModelChange = new EventEmitter();

    /**
     * @function setMode
     * @desc set the mode, update team-related attributes and emit the changes to the parent component to notice changes
     * @param mode chosen exercise solving mode of type {ExerciseMode}
     */
    setMode(mode: ExerciseMode) {
        this.exercise.mode = mode;
        this.updateTeamAttributes(mode);
        this.ngModelChange.emit();
    }

    private updateTeamAttributes(mode: ExerciseMode) {
        if (mode === ExerciseMode.TEAM) {
            this.exercise.teamAssignmentConfig = new TeamAssignmentConfig();
        } else {
            this.exercise.teamAssignmentConfig = null;
        }
    }
}
