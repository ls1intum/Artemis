import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { Team } from 'app/entities/team.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { formatTeamAsSearchResult } from 'app/exercises/shared/team/team.utils';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-teams',
    templateUrl: './teams.component.html',
})
export class TeamsComponent implements OnInit, OnDestroy {
    ButtonSize = ButtonSize;

    teams: Team[] = [];
    filteredTeamsSize = 0;
    paramSub: Subscription;
    exercise: Exercise;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isLoading: boolean;

    currentUser: User;
    isAdmin = false;

    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private exerciseService: ExerciseService,
        private teamService: TeamService,
        private accountService: AccountService,
    ) {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
            this.isAdmin = this.accountService.isAdmin();
        });
    }

    ngOnInit() {
        this.loadAll();
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    loadAll() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.isLoading = true;
            this.exerciseService.find(params['exerciseId']).subscribe((exerciseResponse) => {
                this.exercise = exerciseResponse.body!;
                this.teamService.findAllByExerciseId(params['exerciseId']).subscribe((teamsResponse) => {
                    this.teams = teamsResponse.body!;
                    this.isLoading = false;
                });
            });
        });
    }

    /**
     * Called when a team has been added or was updated by TeamUpdateButtonComponent
     *
     * @param team Team that was added or updated
     */
    onTeamUpdate(team: Team) {
        this.upsertTeam(team);
    }

    /**
     * Called when a team has been deleted by TeamDeleteButtonComponent
     *
     * @param team Team that was deleted
     */
    onTeamDelete(team: Team) {
        this.deleteTeam(team);
    }

    /**
     * Update the number of filtered teams
     *
     * @param filteredTeamsSize Total number of teams after filters have been applied
     */
    handleTeamsSizeChange = (filteredTeamsSize: number) => {
        this.filteredTeamsSize = filteredTeamsSize;
    };

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param team
     */
    searchResultFormatter = formatTeamAsSearchResult;

    /**
     * Converts a team object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param team Team that was selected
     */
    searchTextFromTeam = (team: Team): string => {
        return team.shortName;
    };

    /**
     * If team does not yet exists in teams, it is appended.
     * If team already exists in teams, it is updated.
     *
     * @param team Team that is added or updated
     */
    private upsertTeam(team: Team) {
        const index = this.teams.findIndex((t) => t.id === team.id);
        if (index === -1) {
            this.teams = [...this.teams, team];
        } else {
            this.teams = Object.assign([], this.teams, { [index]: team });
        }
    }

    /**
     * Deleted an existing team from teams.
     *
     * @param team Team that is deleted
     */
    private deleteTeam(team: Team) {
        this.teams = this.teams.filter((t) => t.id !== team.id);
    }
}
