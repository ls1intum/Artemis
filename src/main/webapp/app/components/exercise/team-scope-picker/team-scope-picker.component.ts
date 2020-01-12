import { Component, EventEmitter, Input, Output } from '@angular/core';
import { TeamScope, Exercise } from 'app/entities/exercise';

@Component({
    selector: 'jhi-team-scope-picker',
    templateUrl: './team-scope-picker.component.html',
    styles: ['div { cursor: pointer; }'],
})
export class TeamScopePickerComponent {
    readonly EXERCISE = TeamScope.EXERCISE;
    readonly COURSE = TeamScope.COURSE;

    @Input() exercise: Exercise;
    @Output() ngModelChange = new EventEmitter();

    /**
     * @function setTeamScope
     * @desc set the team scope and emit the changes to the parent component to notice changes
     * @param teamScope chosen team scope of type {TeamScope}
     */
    setTeamScope(teamScope: TeamScope) {
        this.exercise.teamScope = teamScope;
        this.ngModelChange.emit();
    }
}
