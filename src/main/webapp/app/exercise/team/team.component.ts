import { Component, OnInit, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamService } from 'app/exercise/team/team.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { User } from 'app/core/user/user.model';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
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

    team = signal<Team | undefined>(undefined);
    exercise = signal<Exercise | undefined>(undefined);
    isLoading = signal(false);
    isTransitioning = signal(false);

    currentUser = signal<User | undefined>(undefined);
    isAdmin = signal(false);
    readonly isTeamOwner = computed(() => {
        const currentUser = this.currentUser();
        const team = this.team();
        return currentUser !== undefined && team !== undefined && currentUser.id === team.owner?.id;
    });

    constructor() {
        this.accountService.identity().then((user: User) => {
            this.currentUser.set(user);
            this.isAdmin.set(this.accountService.isAdmin());
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
                        this.exercise.set(exerciseResponse.body!);
                        this.teamService.find(this.exercise()!, params['teamId']).subscribe({
                            next: (teamResponse) => {
                                this.team.set(teamResponse.body!);
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

    private setLoadingState(loading: boolean) {
        if (this.exercise() && this.team() && !this.isLoading()) {
            this.isTransitioning.set(loading);
        } else {
            this.isLoading.set(loading);
        }
    }

    private onLoadError = (error: any) => {
        this.alertService.error(error.message);
        this.isLoading.set(false);
    };

    /**
     * Called when the team was updated by TeamUpdateButtonComponent
     *
     * @param team Updated team
     */
    onTeamUpdate(team: Team) {
        this.team.set(team);
    }

    /**
     * Called when the team was deleted by TeamDeleteButtonComponent
     *
     * Navigates back to the overviews of teams for the exercise.
     */
    onTeamDelete() {
        const exercise = this.exercise();
        this.router.navigate(['/course-management', exercise?.course?.id, 'exercises', exercise?.id, 'teams']);
    }
}
