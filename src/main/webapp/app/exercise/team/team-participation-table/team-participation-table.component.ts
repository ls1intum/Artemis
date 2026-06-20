import { Component, OnInit, ViewEncapsulation, computed, inject, input, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { TeamService } from 'app/exercise/team/team.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { get } from 'lodash-es';
import { HttpErrorResponse } from '@angular/common/http';
import { Submission, SubmissionExerciseType, getLatestSubmissionResult, setLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { AccountService } from 'app/core/auth/account.service';
import { onError } from 'app/foundation/util/global.utils';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { getLinkToSubmissionAssessment } from 'app/foundation/util/navigation.utils';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { AssessmentWarningComponent } from 'app/assessment/manage/assessment-warning/assessment-warning.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CellTemplateRef, ColumnDef, TableViewComponent, TableViewOptions } from 'app/shared-ui/table-view/table-view';

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
    imports: [AssessmentWarningComponent, TranslateDirective, RouterLink, ButtonDirective, Tooltip, ArtemisDatePipe, ArtemisTranslatePipe, TableViewComponent],
})
export class TeamParticipationTableComponent implements OnInit {
    private teamService = inject(TeamService);
    private alertService = inject(AlertService);
    private router = inject(Router);
    private accountService = inject(AccountService);

    readonly team = input.required<Team>();
    readonly course = input.required<Course>();
    readonly exercise = input.required<Exercise>();
    readonly isAdmin = input(false);
    readonly isTeamOwner = input(false);

    exercises = signal<ExerciseForTeam[]>([]);
    submissions = signal<Submission[]>([]);
    isLoading = signal<boolean>(false);

    // Cell templates
    readonly titleTemplate = viewChild<CellTemplateRef<ExerciseForTeam>>('titleTemplate');
    readonly releaseDateTemplate = viewChild<CellTemplateRef<ExerciseForTeam>>('releaseDateTemplate');
    readonly dueDateTemplate = viewChild<CellTemplateRef<ExerciseForTeam>>('dueDateTemplate');
    readonly viewTemplate = viewChild<CellTemplateRef<ExerciseForTeam>>('viewTemplate');
    readonly participationTemplate = viewChild<CellTemplateRef<ExerciseForTeam>>('participationTemplate');
    readonly initDateTemplate = viewChild<CellTemplateRef<ExerciseForTeam>>('initDateTemplate');
    readonly assessmentTemplate = viewChild<CellTemplateRef<ExerciseForTeam>>('assessmentTemplate');

    /**
     * Computes the class for a row (used to highlight the exercise to which the current team belongs to)
     */
    readonly rowClass = computed(() => {
        const currentId = this.exercise().id;
        return (row: ExerciseForTeam) => (row.id === currentId ? 'team-participation-current-exercise' : '');
    });

    readonly tableOptions: TableViewOptions = {
        lazy: false,
        paginated: false,
        showSearch: false,
        scrollable: true,
        tableStyle: { 'min-width': '60rem' },
    };

    readonly columns = computed<ColumnDef<ExerciseForTeam>[]>(() => {
        const participationCol: ColumnDef<ExerciseForTeam>[] = this.isAdmin()
            ? [
                  {
                      field: 'participation.id',
                      headerKey: 'artemisApp.team.detail.participationsTable.columns.participationId.label',
                      sort: true,
                      width: '9rem',
                      templateRef: this.participationTemplate(),
                  },
              ]
            : [];

        const assessmentCol: ColumnDef<ExerciseForTeam>[] =
            this.isTeamOwner() || this.exercise().isAtLeastInstructor
                ? [
                      {
                          headerKey: 'artemisApp.team.detail.participationsTable.columns.assessment.label',
                          sort: false,
                          width: '14rem',
                          templateRef: this.assessmentTemplate(),
                      },
                  ]
                : [];

        return [
            { field: 'title', headerKey: 'artemisApp.exercise.exercise', sort: true, width: '12rem', frozen: true, alignFrozen: 'left', templateRef: this.titleTemplate() },
            {
                field: 'releaseDate',
                headerKey: 'artemisApp.exercise.releaseDate',
                sort: true,
                width: '10rem',
                frozen: true,
                alignFrozen: 'left',
                templateRef: this.releaseDateTemplate(),
            },
            {
                field: 'individualDueDate',
                headerKey: 'artemisApp.exercise.dueDate',
                sort: true,
                width: '10rem',
                frozen: true,
                alignFrozen: 'left',
                templateRef: this.dueDateTemplate(),
            },
            { header: '', width: '8rem', frozen: true, alignFrozen: 'left', templateRef: this.viewTemplate() },
            ...participationCol,
            {
                field: 'participation.initializationDate',
                headerKey: 'artemisApp.participation.initializationDate',
                sort: true,
                width: '12rem',
                templateRef: this.initDateTemplate(),
            },
            ...assessmentCol,
        ];
    });

    ngOnInit(): void {
        this.loadAll();
    }

    /**
     * Fetches the course with all the team exercises (and participations) in which this team is present.
     * For the team owner tutor or instructors, the participations also contain the latest submission (for assessment).
     */
    loadAll() {
        this.isLoading.set(true);
        this.teamService.findCourseWithExercisesAndParticipationsForTeam(this.course(), this.team()).subscribe({
            next: (courseResponse) => {
                const exercises = this.transformExercisesFromServer(courseResponse.body!.exercises || []).map((exercise) => {
                    return {
                        ...exercise,
                        isAtLeastTutor: this.accountService.isAtLeastTutorInCourse(exercise.course),
                        isAtLeastEditor: this.accountService.isAtLeastEditorInCourse(exercise.course),
                        isAtLeastInstructor: this.accountService.isAtLeastInstructorInCourse(exercise.course),
                    };
                });
                this.exercises.set(exercises);
                this.submissions.set(exercises.filter((exercise) => exercise.submission).map((exercise) => exercise.submission!));
                this.isLoading.set(false);
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
            }
            return exercise;
        });
    }

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
        return getLinkToSubmissionAssessment(exercise.type!, this.course().id!, exercise.id!, participation?.id, submissionUrlParameter, undefined, undefined);
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
        this.isLoading.set(false);
    }
}
