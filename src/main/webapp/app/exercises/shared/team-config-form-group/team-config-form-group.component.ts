import { Component, Input, OnInit } from '@angular/core';
import { TeamAssignmentConfig } from 'app/entities/team-assignment-config.model';
import { cloneDeep } from 'lodash';
import { Exercise, ExerciseMode } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-team-config-form-group',
    templateUrl: './team-config-form-group.component.html',
    styleUrls: ['./team-config-form-group.component.scss'],
})
export class TeamConfigFormGroupComponent implements OnInit {
    readonly INDIVIDUAL = ExerciseMode.INDIVIDUAL;
    readonly TEAM = ExerciseMode.TEAM;

    @Input() exercise: Exercise;

    config: TeamAssignmentConfig;

    ngOnInit() {
        this.config = this.exercise.teamAssignmentConfig || new TeamAssignmentConfig();
    }

    get changeExerciseModeDisabled(): boolean {
        return Boolean(this.exercise.id);
    }

    onExerciseModeChange(mode: ExerciseMode) {
        if (mode === ExerciseMode.TEAM) {
            this.applyCurrentConfig();
        } else {
            this.exercise.teamAssignmentConfig = null;
        }
    }

    updateMinTeamSize(minTeamSize: number) {
        this.config.maxTeamSize = Math.max(this.config.maxTeamSize, minTeamSize);
        this.applyCurrentConfig();
    }

    updateMaxTeamSize(maxTeamSize: number) {
        this.config.minTeamSize = Math.min(this.config.minTeamSize, maxTeamSize);
        this.applyCurrentConfig();
    }

    private applyCurrentConfig() {
        this.exercise.teamAssignmentConfig = cloneDeep(this.config);
    }
}
