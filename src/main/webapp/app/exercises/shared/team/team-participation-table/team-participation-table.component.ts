import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { Team } from 'app/entities/team.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/core/alert/alert.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { get } from 'lodash';

const currentExerciseRowClass = 'datatable-row-current-exercise';

class ExerciseWithTeamAndOptionalParticipation extends Exercise {
    team: Team;
    participation?: StudentParticipation;
}

@Component({
    selector: 'jhi-team-participation-table',
    templateUrl: './team-participation-table.component.html',
    styleUrls: ['./team-participation-table.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TeamParticipationTableComponent implements OnInit {
    readonly ExerciseType = ExerciseType;
    readonly moment = moment;
    readonly cellClassLeftSpace = 'datatable-cell-space-left';

    @Input() team: Team;
    @Input() course: Course;
    @Input() exercise: Exercise;
    @Input() isAdmin = false;

    exercises: ExerciseWithTeamAndOptionalParticipation[];
    isLoading: boolean;

    constructor(private teamService: TeamService, private jhiAlertService: AlertService) {}

    ngOnInit(): void {
        this.isLoading = true;
        this.teamService.findCourseWithExercisesAndParticipationsForTeam(this.course, this.team).subscribe(
            (courseResponse) => {
                this.exercises = this.transformExercisesFromServer(courseResponse.body!.exercises || []);
                this.isLoading = false;
            },
            (error) => {
                console.error(error);
                this.jhiAlertService.error(error.message);
                this.isLoading = false;
            },
        );
    }

    transformExercisesFromServer(exercises: Exercise[]): ExerciseWithTeamAndOptionalParticipation[] {
        return exercises.map((exercise: ExerciseWithTeamAndOptionalParticipation) => {
            exercise.team = exercise.teams[0];
            exercise.participation = get(exercise, 'studentParticipations[0]', null);
            return exercise;
        });
    }

    /**
     * Computes the class for a row (used to highlight the exercise to which the current team belongs to)
     *
     * @param exercise Exercise is passed in from the template (instead of doing this.exercise) to trigger the ngx-datatable change detection
     */
    rowClass = (exercise: Exercise) => (row: Exercise): string => {
        return exercise.id === row.id ? currentExerciseRowClass : '';
    };
}
