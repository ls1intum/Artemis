import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { Router } from '@angular/router';
import { Team } from 'app/entities/team.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/core/alert/alert.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { get } from 'lodash';
import { HttpErrorResponse } from '@angular/common/http';
import { Submission } from 'app/entities/submission.model';

const currentExerciseRowClass = 'datatable-row-current-exercise';

class ExerciseForTeam extends Exercise {
    team: Team;
    participation?: StudentParticipation;
    submission?: Submission;
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
    @Input() isTeamOwner = false;

    exercises: ExerciseForTeam[];
    isLoading: boolean;

    constructor(private teamService: TeamService, private jhiAlertService: AlertService, private router: Router) {}

    /**
     * Loads all needed data from the server for this component
     */
    ngOnInit(): void {
        this.loadAll();
    }

    /**
     * Fetches the course with all the team exercises (and participations) in which this team is present
     * For the team owner tutor or instructors, the participations also contains the latest submission (for assessment)
     */
    loadAll() {
        this.isLoading = true;
        this.teamService.findCourseWithExercisesAndParticipationsForTeam(this.course, this.team).subscribe((courseResponse) => {
            this.exercises = this.transformExercisesFromServer(courseResponse.body!.exercises || []);
            this.isLoading = false;
        }, this.onError);
    }

    /**
     * Assigns this team, the participation and latest submission directly onto the exercise for easier access
     * @param exercises Exercises from the server which to transform
     */
    transformExercisesFromServer(exercises: Exercise[]): ExerciseForTeam[] {
        return exercises.map((exercise: ExerciseForTeam) => {
            exercise.team = exercise.teams[0];
            exercise.participation = get(exercise, 'studentParticipations[0]', null);
            exercise.submission = get(exercise, 'participation.submissions[0]', null); // only exists for instructor and team tutor
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

    /**
     * Uses the router to navigate to the assessment editor for a given/new submission
     * @param exercise Exercise to which the submission belongs
     * @param submission Either submission or 'new'
     */
    async openAssessmentEditor(exercise: Exercise, submission: Submission | 'new'): Promise<void> {
        const submissionUrlParameter: number | 'new' = submission === 'new' ? 'new' : submission.id;
        const route = `/course-management/${this.course.id}/${exercise.type}-exercises/${exercise.id}/submissions/${submissionUrlParameter}/assessment`;
        await this.router.navigate([route]);
    }

    /**
     * Calculates the status of a submission by inspecting the result
     * @param submission Submission which to check
     */
    calculateStatus(submission: Submission) {
        if (submission.result && submission.result.completionDate) {
            return 'DONE';
        }
        return 'DRAFT';
    }

    private onError(error: HttpErrorResponse) {
        console.error(error);
        this.jhiAlertService.error(error.message);
        this.isLoading = false;
    }
}
