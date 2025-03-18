import { Component, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Team } from 'app/entities/team.model';
import { TeamService } from 'app/exercise/team/team.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercise/exercise.service';
import { User } from 'app/core/user/user.model';
import { ButtonSize } from 'app/shared/components/button.component';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TeamUpdateButtonComponent } from './team-update-dialog/team-update-button.component';
import { TeamDeleteButtonComponent } from './team-update-dialog/team-delete-button.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TeamParticipationTableComponent } from './team-participation-table/team-participation-table.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-team',
    templateUrl: './team.component.html',
    styleUrls: ['./team.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        RouterLink,
        TranslateDirective,
        NgbTooltip,
        TeamUpdateButtonComponent,
        TeamDeleteButtonComponent,
        DataTableComponent,
        NgxDatatableModule,
        FaIconComponent,
        TeamParticipationTableComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
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
