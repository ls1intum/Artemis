import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { ActivatedRoute } from '@angular/router';
import { Team } from 'app/entities/team.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { User } from 'app/core/user/user.model';
import { ButtonSize } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-team',
    templateUrl: './team.component.html',
})
export class TeamComponent implements OnInit {
    ButtonSize = ButtonSize;

    team: Team;
    exercise: Exercise;

    filteredStudentsSize = 0;

    paramSub: Subscription;
    isLoading: boolean;

    constructor(
        private route: ActivatedRoute,
        private participationService: ParticipationService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private exerciseService: ExerciseService,
        private teamService: TeamService,
        private router: Router,
    ) {}

    ngOnInit() {
        this.load();
    }

    load() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.isLoading = true;
            this.exerciseService.find(params['exerciseId']).subscribe((exerciseResponse) => {
                this.exercise = exerciseResponse.body!;
                this.teamService.find(this.exercise, params['teamId']).subscribe((teamResponse) => {
                    this.team = teamResponse.body!;
                    this.isLoading = false;
                });
            });
        });
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

    /**
     * Update the number of filtered students
     *
     * @param filteredStudentsSize Total number of students after filters have been applied
     */
    handleStudentsSizeChange = (filteredStudentsSize: number) => {
        this.filteredStudentsSize = filteredStudentsSize;
    };

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param student
     */
    searchResultFormatter = (student: User) => {
        const { login, name } = student;
        return `${name} (${login})`;
    };

    /**
     * Converts a student object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param student Student that was selected
     */
    searchTextFromStudent = (student: User): string => {
        return student.login || '';
    };
}
