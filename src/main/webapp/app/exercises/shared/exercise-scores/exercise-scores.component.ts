import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { DifferencePipe } from 'ngx-moment';
import * as moment from 'moment';
import { Moment } from 'moment';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { take } from 'rxjs/operators';
import { of, zip, forkJoin } from 'rxjs';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { areManualResultsAllowed } from 'app/exercises/shared/exercise/exercise-utils';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { formatTeamAsSearchResult } from 'app/exercises/shared/team/team.utils';
import { AccountService } from 'app/core/auth/account.service';
import { defaultLongDateTimeFormat } from 'app/shared/pipes/artemis-date.pipe';
import { getLinkToSubmissionAssessment } from 'app/utils/navigation.utils';

/**
 * Filter properties for a result
 */
enum FilterProp {
    ALL = 'all',
    SUCCESSFUL = 'successful',
    UNSUCCESSFUL = 'unsuccessful',
    BUILD_FAILED = 'build-failed',
    MANUAL = 'manual',
    AUTOMATIC = 'automatic',
}

@Component({
    selector: 'jhi-exercise-scores',
    styleUrls: ['./exercise-scores.component.scss'],
    templateUrl: './exercise-scores.component.html',
    providers: [ModelingAssessmentService, SourceTreeService],
    encapsulation: ViewEncapsulation.None,
})
export class ExerciseScoresComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly FilterProp = FilterProp;
    readonly ExerciseType = ExerciseType;
    readonly FeatureToggle = FeatureToggle;

    course: Course;
    exercise: Exercise;
    paramSub: Subscription;
    reverse: boolean;
    results: Result[];
    filteredResultsSize: number;
    eventSubscriber: Subscription;
    newManualResultAllowed: boolean;

    resultCriteria: {
        filterProp: FilterProp;
    };

    isLoading: boolean;

    isAdmin = false;

    constructor(
        private route: ActivatedRoute,
        private momentDiff: DifferencePipe,
        private courseService: CourseManagementService,
        private exerciseService: ExerciseService,
        private accountService: AccountService,
        private resultService: ResultService,
        private profileService: ProfileService,
        private programmingSubmissionService: ProgrammingSubmissionService,
    ) {
        this.resultCriteria = {
            filterProp: FilterProp.ALL,
        };
        this.results = [];
        this.filteredResultsSize = 0;
    }

    /**
     * Fetches the course and exercise from the server
     */
    ngOnInit() {
        this.paramSub = this.route.params.subscribe((params) => {
            this.isLoading = true;
            const findCourse = this.courseService.find(params['courseId']);
            const findExercise = this.exerciseService.find(params['exerciseId']);

            forkJoin(findCourse, findExercise).subscribe(([courseRes, exerciseRes]) => {
                this.course = courseRes.body!;
                this.exercise = exerciseRes.body!;
                // After both calls are done, the loading flag is removed. If the exercise is not a programming exercise, only the result call is needed.
                zip(this.resultService.getResults(this.exercise), this.loadAndCacheProgrammingExerciseSubmissionState())
                    .pipe(take(1))
                    .subscribe((results) => {
                        this.results = results[0].body || [];
                        this.isLoading = false;
                    });
                this.exercise.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(this.course || this.exercise.exerciseGroup!.exam!.course);
                this.exercise.isAtLeastEditor = this.accountService.isAtLeastEditorInCourse(this.course || this.exercise.exerciseGroup!.exam!.course);
                this.exercise.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(this.course || this.exercise.exerciseGroup!.exam!.course);
                this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
                this.isAdmin = this.accountService.isAdmin();
            });
        });
    }

    getExerciseParticipationsLink(participationId: number): string[] {
        return !!this.exercise.exerciseGroup
            ? [
                  '/course-management',
                  this.course.id!.toString(),
                  'exams',
                  this.exercise.exerciseGroup!.exam!.id!.toString(),
                  'exercise-groups',
                  this.exercise.exerciseGroup!.id!.toString(),
                  this.exercise.type + '-exercises',
                  this.exercise.id!.toString(),
                  'participations',
                  participationId.toString(),
              ]
            : [
                  '/course-management',
                  this.course.id!.toString(),
                  this.exercise.type + '-exercises',
                  this.exercise.id!.toString(),
                  'participations',
                  participationId.toString(),
                  'submissions',
              ];
    }

    /**
     * We need to preload the pending submissions here, otherwise every updating-result would trigger a single REST call.
     * Will return immediately if the exercise is not of type PROGRAMMING.
     */
    private loadAndCacheProgrammingExerciseSubmissionState() {
        // TODO: this is deactivated because of performance reasons as it would lead quickly to thousands of subscribed websocket topics
        // return this.exercise.type === ExerciseType.PROGRAMMING ? this.programmingSubmissionService.getSubmissionStateOfExercise(this.exercise.id) : of(undefined);
        return of(undefined);
    }

    /**
     * Updates the criteria by which to filter results
     * @param newValue New filter prop value
     */
    updateResultFilter(newValue: FilterProp) {
        this.isLoading = true;
        setTimeout(() => {
            this.resultCriteria.filterProp = newValue;
            this.isLoading = false;
        });
    }

    /**
     * Predicate used to filter results by the current filter prop setting
     * @param result Result for which to evaluate the predicate
     */
    filterResultByProp = (result: Result): boolean => {
        switch (this.resultCriteria.filterProp) {
            case FilterProp.SUCCESSFUL:
                return !!result.successful;
            case FilterProp.UNSUCCESSFUL:
                return !result.successful;
            case FilterProp.BUILD_FAILED:
                return (
                    !!result.submission &&
                    result.submission.submissionExerciseType === SubmissionExerciseType.PROGRAMMING &&
                    !!(result.submission as ProgrammingSubmission).buildFailed
                );
            case FilterProp.MANUAL:
                return Result.isManualResult(result);
            case FilterProp.AUTOMATIC:
                return result.assessmentType === AssessmentType.AUTOMATIC;
            default:
                return true;
        }
    };

    /**
     * returns the route for the assessment page for all types of exercises
     * @param exercise exercise of the submission
     * @param submissionId id of the submission to be assessed
     * @param participationId id of the participation to the submission
     * @param resultId id of the result
     */
    getAssessmentLink(exercise: Exercise, submissionId: number, participationId: number, resultId?: number) {
        if (!exercise || !exercise.type) {
            return;
        }
        const examId = this.exercise.exerciseGroup?.exam ? this.exercise.exerciseGroup!.exam!.id! : 0;
        return getLinkToSubmissionAssessment(exercise.type, this.exercise.course!.id!, exercise.id!, participationId, submissionId, examId, exercise.exerciseGroup?.id!, resultId);
    }

    /**
     * Update the number of filtered results
     *
     * @param filteredResultsSize Total number of results after filters have been applied
     */
    handleResultsSizeChange = (filteredResultsSize: number) => {
        this.filteredResultsSize = filteredResultsSize;
    };

    /**
     * Returns the build plan id for a result
     * @param result Result for which to return the build plan id
     */
    buildPlanId(result: Result) {
        return (result.participation as ProgrammingExerciseStudentParticipation)?.buildPlanId;
    }

    /**
     * Returns the project key of the exercise
     */
    projectKey(): string {
        return (this.exercise as ProgrammingExercise).projectKey!;
    }

    /**
     * Returns the link to the repository of a result
     * @param result Result for which to get the link for
     */
    getRepositoryLink(result: Result) {
        return (result.participation! as ProgrammingExerciseStudentParticipation).userIndependentRepositoryUrl;
    }

    /**
     * Exports the names of exercise participants as a csv file
     */
    exportNames() {
        if (this.results.length > 0) {
            const rows: string[] = [];
            this.results.forEach((result, index) => {
                const studentParticipation = result.participation! as StudentParticipation;
                const { participantName } = studentParticipation;
                if (studentParticipation.team) {
                    if (index === 0) {
                        rows.push('data:text/csv;charset=utf-8,Team Name,Team Short Name,Students');
                    }
                    const { name, shortName, students } = studentParticipation.team;
                    rows.push(`${name},${shortName},"${students?.map((s) => s.name).join(', ')}"`);
                } else {
                    rows.push(index === 0 ? `data:text/csv;charset=utf-8,${participantName}` : participantName!);
                }
            });
            this.resultService.triggerDownloadCSV(rows, 'results-names.csv');
        }
    }

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param result
     */
    searchResultFormatter = (result: Result): string => {
        const participation = result.participation as StudentParticipation;
        if (participation.student) {
            const { login, name } = participation.student;
            return `${login} (${name})`;
        } else if (participation.team) {
            return formatTeamAsSearchResult(participation.team);
        }
        return '';
    };

    /**
     * Converts a result object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param result
     */
    searchTextFromResult = (result: Result): string => {
        return (result.participation as StudentParticipation).participantIdentifier || '';
    };

    /**
     * Triggers a re-fetch of the results from the server
     */
    refresh() {
        this.isLoading = true;
        this.results = [];
        this.resultService.getResults(this.exercise).subscribe((results) => {
            this.results = results.body || [];
            this.isLoading = false;
        });
    }

    /**
     * Unsubscribes from all subscriptions
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.programmingSubmissionService.unsubscribeAllWebsocketTopics(this.exercise);
    }

    formatDate(date: Moment | Date | undefined) {
        // TODO: we should try to use the artemis date pipe here
        return date ? moment(date).format(defaultLongDateTimeFormat) : '';
    }
}
