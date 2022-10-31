import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { Router } from '@angular/router';
import { Team } from 'app/entities/team.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { AlertService } from 'app/core/util/alert.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { get } from 'lodash-es';
import { HttpErrorResponse } from '@angular/common/http';
import { Submission, SubmissionExerciseType, getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { AccountService } from 'app/core/auth/account.service';
import { onError } from 'app/shared/util/global.utils';
import { Participation } from 'app/entities/participation/participation.model';
import { getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { faFlag, faFolderOpen } from '@fortawesome/free-solid-svg-icons';

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
    individualDueDate?: dayjs.Dayjs;
}

@Component({
    selector: 'jhi-team-participation-table',
    templateUrl: './team-participation-table.component.html',
    styleUrls: ['./team-participation-table.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TeamParticipationTableComponent implements OnInit {
    readonly ExerciseType = ExerciseType;
    readonly dayjs = dayjs;

    @Input() team: Team;
    @Input() course: Course;
    @Input() exercise: Exercise;
    @Input() isAdmin = false;
    @Input() isTeamOwner = false;

    exercises: ExerciseForTeam[] = [];
    submissions: Submission[] = [];
    isLoading: boolean;

    // Icons
    faFolderOpen = faFolderOpen;
    faFlag = faFlag;

    constructor(private teamService: TeamService, private alertService: AlertService, private router: Router, private accountService: AccountService) {}

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
        this.teamService.findCourseWithExercisesAndParticipationsForTeam(this.course, this.team).subscribe({
            next: (courseResponse) => {
                this.exercises = this.transformExercisesFromServer(courseResponse.body!.exercises || []).map((exercise) => {
                    return {
                        ...exercise,
                        isAtLeastTutor: this.accountService.isAtLeastTutorInCourse(exercise.course),
                        isAtLeastEditor: this.accountService.isAtLeastEditorInCourse(exercise.course),
                        isAtLeastInstructor: this.accountService.isAtLeastInstructorInCourse(exercise.course),
                    };
                });
                this.submissions = this.exercises.filter((exercise) => exercise.submission).map((exercise) => exercise.submission!);
                this.isLoading = false;
            },
            error: (error) => this.onError(error),
        });
    }

    /**
     * Assigns this team, the participation and latest submission directly onto the exercise for easier access
     * @param exercises Exercises from the server which to transform
     */
    transformExercisesFromServer(exercises: Exercise[]): ExerciseForTeam[] {
        return ExerciseService.convertExercisesDateFromServer(exercises).map((exercise: ExerciseForTeam) => {
            exercise.team = exercise.teams![0];
            const participation = get(exercise, 'studentParticipations[0]', undefined);
            exercise.participation = participation;
            exercise.individualDueDate = getExerciseDueDate(exercise, exercise.participation);
            exercise.submission = get(exercise, 'participation.submissions[0]', undefined); // only exists for instructor and team tutor
            if (exercise.submission) {
                exercise.submission.participation = participation;

                setLatestSubmissionResult(exercise.submission, get(exercise, 'participation.results[0]', undefined));
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
     * @param participation Participation for which the editor should be opened
     * @param submission Either submission or 'new'
     */
    async openAssessmentEditor(exercise: Exercise, participation: Participation, submission: Submission | 'new'): Promise<void> {
        const route = this.getAssessmentLink(exercise, participation, submission);
        await this.router.navigate(route);
    }

    /**
     * Generates and returns the link that leads to the assessment editor
     * @param exercise Exercise to which the submission belongs
     * @param participation Participation for which the editor should be opened
     * @param submission Either submission or 'new'
     */
    getAssessmentLink(exercise: Exercise, participation: Participation | undefined, submission: Submission | 'new' | undefined): string[] {
        const submissionUrlParameter: number | 'new' = submission === 'new' || submission == undefined ? 'new' : submission.id!;
        return getLinkToSubmissionAssessment(exercise.type!, this.course.id!, exercise.id!, participation?.id, submissionUrlParameter, 0, 0);
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
    isAssessmentButtonDisabled(exercise: Exercise, submission?: Submission): boolean {
        // Non-Submitted exercises can not be assessed
        if (!submission || !submission.submitted) {
            return true;
        }
        // Exercises without due date can be assessed
        if (!exercise.dueDate) {
            return false;
        }
        // Programming exercises can only be assessed by anyone / all other exercises can be assessed by tutors
        // if the exercise due date has passed
        if (exercise.type === ExerciseType.PROGRAMMING || !exercise.isAtLeastInstructor) {
            return !hasExerciseDueDatePassed(exercise, submission.participation);
        } else if (exercise.isAtLeastInstructor) {
            return false;
        }
        return true;
    }

    private onError(error: HttpErrorResponse) {
        onError(this.alertService, error);
        this.isLoading = false;
    }
}
