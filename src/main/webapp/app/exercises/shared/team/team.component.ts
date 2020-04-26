import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { Router } from '@angular/router';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { ActivatedRoute } from '@angular/router';
import { Team } from 'app/entities/team.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { User } from 'app/core/user/user.model';
import { ButtonSize } from 'app/shared/components/button.component';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-team',
    templateUrl: './team.component.html',
    styles: ['.date-tooltip { width: 115px !important }', '.team-short-name { font-size: 100% }'],
    encapsulation: ViewEncapsulation.None,
})
export class TeamComponent implements OnInit {
    ButtonSize = ButtonSize;

    team: Team;
    exercise: Exercise;
    isLoading: boolean;

    currentUser: User;
    isAdmin = false;

    constructor(
        private route: ActivatedRoute,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private exerciseService: ExerciseService,
        private teamService: TeamService,
        private accountService: AccountService,
        private router: Router,
    ) {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
            this.isAdmin = this.accountService.isAdmin();
        });
    }

    ngOnInit() {
        this.load();
    }

    load() {
        this.route.params.subscribe(
            (params) => {
                this.isLoading = true;
                this.exerciseService.find(params['exerciseId']).subscribe((exerciseResponse) => {
                    this.exercise = exerciseResponse.body!;
                    this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.exercise.course!);
                    this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.exercise.course!);
                    this.teamService.find(this.exercise, params['teamId']).subscribe((teamResponse) => {
                        this.team = teamResponse.body!;
                        console.log(this.exercise);
                        this.isLoading = false;
                    });
                });
            },
            (error) => console.log(error),
        );
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
     *
     * @param team Deleted team
     */
    onTeamDelete(team: Team) {
        this.router.navigate(['/course-management', this.exercise.course?.id, 'exercises', this.exercise.id, 'teams']);
    }
}
