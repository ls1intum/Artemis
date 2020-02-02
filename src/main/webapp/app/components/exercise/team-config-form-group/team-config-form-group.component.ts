import { Component, Input } from '@angular/core';
import { Exercise, ExerciseMode } from 'app/entities/exercise';

@Component({
    selector: 'jhi-team-config-form-group',
    templateUrl: './team-config-form-group.component.html',
    styleUrls: ['./team-config-form-group.component.scss'],
})
export class TeamConfigFormGroupComponent {
    readonly INDIVIDUAL = ExerciseMode.INDIVIDUAL;
    readonly TEAM = ExerciseMode.TEAM;

    @Input() exercise: Exercise;
}
