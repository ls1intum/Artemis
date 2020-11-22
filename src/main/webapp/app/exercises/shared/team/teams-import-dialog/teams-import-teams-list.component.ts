import { ChangeDetectionStrategy, Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { Team } from 'app/entities/team.model';
import { flatMap } from 'lodash';

@Component({
    selector: 'jhi-teams-import-teams-list',
    templateUrl: './teams-import-teams-list.component.html',
    styleUrls: ['./teams-import-dialog.component.html'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TeamsImportTeamsListComponent implements OnInit, OnChanges {
    @Input() sourceTeams: Team[];
    @Input() teamShortNamesAlreadyExistingInExercise: string[];
    @Input() isSourceTeamFreeOfAnyConflicts: Function;
    @Input() studentLoginsAlreadyExistingInExercise: string[];
    teams: Team[];
    errors: boolean[] = [];
    successes: boolean[] = [];
    constructor() {}

    ngOnInit(): void {}

    ngOnChanges(changes: SimpleChanges) {
        this.teams = changes.sourceTeams.currentValue;
        this.errors = changes.sourceTeams.currentValue.map((sourceTeam: Team) => this.teamShortNamesAlreadyExistingInExercise.includes(sourceTeam.shortName!));
        this.successes = changes.sourceTeams.currentValue.map((sourceTeam: Team) => this.isSourceTeamFreeOfAnyConflicts(sourceTeam));
    }

    /**
     * Returns a sample team that can be used to render the different conflict states in the legend below the source teams
     */
    get sampleTeamForLegend() {
        const team = new Team();
        const student = new User(1, 'ga12abc', 'John', 'Doe', 'john.doe@tum.de');
        student.name = `${student.firstName} ${student.lastName}`;
        team.students = [student];
        return team;
    }

    get sampleErrorStudentLoginsForLegend() {
        return this.sampleTeamForLegend.students!.map((student) => student.login);
    }

    get showLegend() {
        return (
            this.errors.includes(true) ||
            this.successes.includes(false) ||
            flatMap(this.teams, (team) => team.students?.map((student) => student.login && this.studentLoginsAlreadyExistingInExercise.includes(student.login))).includes(true)
        );
    }
}
