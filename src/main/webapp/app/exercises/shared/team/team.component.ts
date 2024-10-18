import { Component, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Team } from 'app/entities/team.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { User } from 'app/core/user/user.model';
import { ButtonSize } from 'app/shared/components/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-team',
    templateUrl: './team.component.html',
    styleUrls: ['./team.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TeamComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private exerciseService = inject(ExerciseService);
    private teamService = inject(TeamService);
    private accountService = inject(AccountService);
    private router = inject(Router);

    ButtonSize = ButtonSize;

    team: Team;
    exercise: Exercise;
    isLoading: boolean;
    isTransitioning: boolean;

    currentUser: User;
    isAdmin = false;
    isTeamOwner = false;

    constructor() {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
            this.isAdmin = this.accountService.isAdmin();
            this.setTeamOwnerFlag();
        });
    }

    /**
     * Fetches the exercise and team from the server
     */
    ngOnInit() {
        this.route.params.subscribe({
            next: (params) => {
                this.setLoadingState(true);
                this.exerciseService.find(params['exerciseId']).subscribe({
                    next: (exerciseResponse) => {
                        this.exercise = exerciseResponse.body!;
                        this.teamService.find(this.exercise, params['teamId']).subscribe({
                            next: (teamResponse) => {
                                this.team = teamResponse.body!;
                                this.setTeamOwnerFlag();
                                this.setLoadingState(false);
                            },
                            error: this.onLoadError,
                        });
                    },
                    error: this.onLoadError,
                });
            },
            error: this.onLoadError,
        });
    }

    private setTeamOwnerFlag() {
        if (this.currentUser && this.team) {
            this.isTeamOwner = this.currentUser.id === this.team.owner?.id;
        }
    }

    private setLoadingState(loading: boolean) {
        if (this.exercise && this.team && !this.isLoading) {
            this.isTransitioning = loading;
        } else {
            this.isLoading = loading;
        }
    }

    private onLoadError(error: any) {
        this.alertService.error(error.message);
        this.isLoading = false;
    }

    /**
     * Called when the team was updated by TeamUpdateButtonComponent
     *
     * @param team Updated team
     */
    onTeamUpdate(team: Team) {
        this.team = team;
    }

    /**
     * Called when the team was deleted by TeamDeleteButtonComponent
     *
     * Navigates back to the overviews of teams for the exercise.
     */
    onTeamDelete() {
        this.router.navigate(['/course-management', this.exercise.course?.id, 'exercises', this.exercise.id, 'teams']);
    }
}
