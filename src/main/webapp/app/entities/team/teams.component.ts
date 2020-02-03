import { Component, OnInit, OnDestroy } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { ActivatedRoute } from '@angular/router';
import { Subject } from 'rxjs';
import { Team } from 'app/entities/team/team.model';
import { TeamService } from 'app/entities/team/team.service';

@Component({
    selector: 'jhi-teams',
    templateUrl: './teams.component.html',
})
export class TeamsComponent implements OnInit, OnDestroy {
    teams: Team[] = [];
    filteredTeamsSize = 0;
    paramSub: Subscription;
    exercise: Exercise;

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    isLoading: boolean;

    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private exerciseService: ExerciseService,
        private teamService: TeamService,
    ) {}

    ngOnInit() {
        this.loadAll();
    }

    ngOnDestroy() {
        this.dialogErrorSource.unsubscribe();
    }

    loadAll() {
        this.paramSub = this.route.params.subscribe(params => {
            this.isLoading = true;
            this.exerciseService.find(params['exerciseId']).subscribe(exerciseResponse => {
                this.exercise = exerciseResponse.body!;
                this.teamService.findAllByExerciseId(params['exerciseId']).subscribe(teamsResponse => {
                    this.teams = teamsResponse.body!;
                    this.isLoading = false;
                });
            });
        });
    }

    removeTeam = (team: Team) => {
        this.teamService.delete(team.id).subscribe(
            () => {},
            () => {
                this.jhiAlertService.error('artemisApp.team.removeTeam.error');
            },
        );
    };

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
    searchResultFormatter = (team: Team) => {
        const { name } = team;
        return name;
    };

    /**
     * Converts a team object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param team Team that was selected
     */
    searchTextFromTeam = (team: Team): string => {
        return team.name;
    };
}
