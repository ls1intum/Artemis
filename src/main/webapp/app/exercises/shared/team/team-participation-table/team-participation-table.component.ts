import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { Router } from '@angular/router';
import { Team } from 'app/entities/team.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { Course } from 'app/entities/course.model';
import { JhiAlertService } from 'ng-jhipster';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { get } from 'lodash';
import { HttpErrorResponse } from '@angular/common/http';
import { getLatestSubmissionResult, setLatestSubmissionResult, Submission, SubmissionExerciseType } from 'app/entities/submission.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { AccountService } from 'app/core/auth/account.service';

const currentExerciseRowClass = 'datatable-row-current-exercise';

enum AssessmentAction {
    START = 'start',
    CONTINUE = 'continue',
    OPEN = 'open',
}

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

    constructor(
        private teamService: TeamService,
        private exerciseService: ExerciseService,
        private jhiAlertService: JhiAlertService,
        private router: Router,
        private accountService: AccountService,
    ) {}

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
            this.exercises = this.transformExercisesFromServer(courseResponse.body!.exercises || []).map((exercise) => {
                return {
                    ...exercise,
                    isAtLeastTutor: this.accountService.isAtLeastTutorInCourse(exercise.course!),
                    isAtLeastEditor: this.accountService.isAtLeastEditorInCourse(exercise.course!),
                    isAtLeastInstructor: this.accountService.isAtLeastInstructorInCourse(exercise.course!),
                };
            });
            this.isLoading = false;
        }, this.onError);
    }

    /**
     * Assigns this team, the participation and latest submission directly onto the exercise for easier access
     * @param exercises Exercises from the server which to transform
     */
    transformExercisesFromServer(exercises: Exercise[]): ExerciseForTeam[] {
        return this.exerciseService.convertExercisesDateFromServer(exercises).map((exercise: ExerciseForTeam) => {
            exercise.team = exercise.teams![0];
            const participation = get(exercise, 'studentParticipations[0]', null);
            exercise.participation = participation;
            exercise.submission = get(exercise, 'participation.submissions[0]', null); // only exists for instructor and team tutor
            if (exercise.submission) {
                setLatestSubmissionResult(exercise.submission, get(exercise, 'participation.results[0]', null));
                // assign this value so that it can be used later on in the view hierarchy (e.g. when updating a result, i.e. overriding an assessment
                if (exercise.submission.results) {
                    getLatestSubmissionResult(exercise.submission)!.participation = participation;
                }
            }
            return exercise;
        });
    }

    /**
     * Computes the class for a row (used to highlight the exercise to which the current team belongs to)
     *
     * @param exercise Exercise is passed in from the template (instead of doing this.exercise) to trigger the ngx-datatable change detection
     */
    rowClass =
        (exercise: Exercise) =>
        (row: Exercise): string => {
            return exercise.id === row.id ? currentExerciseRowClass : '';
        };

    /**
     * Uses the router to navigate to the assessment editor for a given/new submission
     * @param exercise Exercise to which the submission belongs
     * @param submission Either submission or 'new'
     */
    async openAssessmentEditor(exercise: Exercise, submission: Submission | 'new'): Promise<void> {
        const submissionUrlParameter: number | 'new' = submission === 'new' ? 'new' : submission.id!;
        const route = `/course-management/${this.course.id}/${exercise.type}-exercises/${exercise.id}/submissions/${submissionUrlParameter}/assessment`;
        await this.router.navigate([route]);
    }

    /**
     * Returns the assessment action depending on the state of the result on the submission
     * @param submission Submission which to check
     */
    assessmentAction(submission?: Submission): AssessmentAction {
        if (
            !submission ||
            !getLatestSubmissionResult(submission) ||
            (submission.submissionExerciseType === SubmissionExerciseType.PROGRAMMING && getLatestSubmissionResult(submission)!.assessmentType === AssessmentType.AUTOMATIC)
        ) {
            return AssessmentAction.START;
        } else if (!getLatestSubmissionResult(submission)!.completionDate) {
            return AssessmentAction.CONTINUE;
        }
        return AssessmentAction.OPEN;
    }

    onActivate() {
        window.scroll(0, 0);
    }

    /**
     * Returns whether the assessment button should be disabled
     * @param exercise Exercise for which the submission is to be assessed
     * @param submission Submission that is to be assessed
     */
    assessmentButtonDisabled(exercise: Exercise, submission: Submission | null) {
        if (submission?.submissionExerciseType === SubmissionExerciseType.PROGRAMMING) {
            // Programming exercise cannot be assessed by anyone if there is no submitted submission or the due date is reached yet
            return !submission?.submitted || exercise.dueDate === null || exercise.dueDate?.isAfter(moment());
        } else if (!exercise.isAtLeastInstructor) {
            // Other exercises cannot be assessed by tutors if there is no submitted submission or submission date is not reached yet
            return !submission?.submitted || exercise.dueDate === null || exercise.dueDate?.isAfter(moment());
        } else {
            // Other exercises cannot be assessed by anyone if there is not submitted submission.
            return !submission?.submitted;
        }
    }

    private onError(error: HttpErrorResponse) {
        this.jhiAlertService.error(error.message);
        this.isLoading = false;
    }
}
