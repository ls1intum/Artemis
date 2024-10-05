import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { Team } from 'app/entities/team.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { ButtonSize } from 'app/shared/components/button.component';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { formatTeamAsSearchResult } from 'app/exercises/shared/team/team.utils';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { AlertService } from 'app/core/util/alert.service';
import { EventManager } from 'app/core/util/event-manager.service';

export enum FilterProp {
    ALL = 'all',
    OWN = 'own',
}

@Component({
    selector: 'jhi-teams',
    templateUrl: './teams.component.html',
})
export class TeamsComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private participationService = inject(ParticipationService);
    private alertService = inject(AlertService);
    private eventManager = inject(EventManager);
    private exerciseService = inject(ExerciseService);
    private teamService = inject(TeamService);
    private accountService = inject(AccountService);

    readonly FilterProp = FilterProp;
    readonly ButtonSize = ButtonSize;

    teams: Team[] = [];
    teamCriteria: { filterProp: FilterProp } = { filterProp: FilterProp.ALL };
    filteredTeamsSize = 0;
    exercise: Exercise;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isLoading: boolean;

    currentUser: User;
    isAdmin = false;

    constructor() {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
            this.isAdmin = this.accountService.isAdmin();
        });
    }

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit() {
        this.initTeamFilter();
        this.loadAll();
    }

    /**
     * Life cycle hook to indicate component destruction is done
     */
    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    /**
     * Load all team components
     */
    loadAll() {
        this.route.params.subscribe((params) => {
            this.isLoading = true;
            this.exerciseService.find(params['exerciseId']).subscribe((exerciseResponse) => {
                this.exercise = exerciseResponse.body!;
                const teamOwnerId = this.teamCriteria.filterProp === FilterProp.OWN ? this.currentUser.id! : undefined;
                this.teamService.findAllByExerciseId(params['exerciseId'], teamOwnerId).subscribe((teamsResponse) => {
                    this.teams = teamsResponse.body!;
                    this.isLoading = false;
                });
            });
        });
    }

    /**
     * Initializes the team filter based on the query param "filter"
     */
    initTeamFilter() {
        switch (this.route.snapshot.queryParamMap.get('filter')) {
            case FilterProp.OWN:
                this.teamCriteria.filterProp = FilterProp.OWN;
                break;
            default:
                this.teamCriteria.filterProp = FilterProp.ALL;
                break;
        }
    }

    /**
     * Updates the criteria by which to filter teams and the filter query param, then reloads teams
     * @param filter New filter prop value
     */
    updateTeamFilter(filter: FilterProp) {
        this.teamCriteria.filterProp = filter;
        this.router.navigate([], { relativeTo: this.route, queryParams: { filter }, queryParamsHandling: 'merge', replaceUrl: true });
        this.loadAll();
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
     * Called when teams were imported from another team exercise
     *
     * @param teams All teams that this exercise has now after the import
     */
    onTeamsImport(teams: Team[]) {
        this.teams = teams;
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
        return team.shortName!;
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
