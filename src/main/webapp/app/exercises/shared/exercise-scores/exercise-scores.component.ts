import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { HttpResponse } from '@angular/common/http';
import { forkJoin, of, Subscription, zip } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { take } from 'rxjs/operators';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { areManualResultsAllowed } from 'app/exercises/shared/exercise/exercise.utils';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { SubmissionExerciseType } from 'app/entities/submission.model';
import { formatTeamAsSearchResult } from 'app/exercises/shared/team/team.utils';
import { AccountService } from 'app/core/auth/account.service';
import { defaultLongDateTimeFormat } from 'app/shared/pipes/artemis-date.pipe';
import { setBuildPlanUrlForProgrammingParticipations } from 'app/exercises/shared/participation/participation.utils';
import { faCodeBranch, faDownload, faFolderOpen, faListAlt, faSync } from '@fortawesome/free-solid-svg-icons';
import { faFileCode } from '@fortawesome/free-regular-svg-icons';
import { Range } from 'app/shared/util/utils';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

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
    encapsulation: ViewEncapsulation.None,
})
export class ExerciseScoresComponent implements OnInit, OnDestroy {
    // make constants available to html for comparison
    readonly FilterProp = FilterProp;
    readonly ExerciseType = ExerciseType;
    readonly FeatureToggle = FeatureToggle;
    // represents all intervals selectable in the score distribution on the exercise statistics
    readonly scoreRanges = [
        new Range(0, 10),
        new Range(10, 20),
        new Range(20, 30),
        new Range(30, 40),
        new Range(40, 50),
        new Range(50, 60),
        new Range(60, 70),
        new Range(70, 80),
        new Range(80, 90),
        new Range(90, 100),
    ];

    course: Course;
    exercise: Exercise;
    paramSub: Subscription;
    reverse: boolean;
    results: Result[];
    filteredResults: Result[];
    filteredResultsSize: number;
    eventSubscriber: Subscription;
    newManualResultAllowed: boolean;
    rangeFilter?: Range;

    resultCriteria: {
        filterProp: FilterProp;
    };

    isLoading: boolean;

    isAdmin = false;

    public practiceMode = false;

    // Icons
    faDownload = faDownload;
    faSync = faSync;
    faFolderOpen = faFolderOpen;
    faListAlt = faListAlt;
    faCodeBranch = faCodeBranch;
    farFileCode = faFileCode;

    constructor(
        private route: ActivatedRoute,
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
            const filterValue = this.route.snapshot.queryParamMap.get('scoreRangeFilter');
            if (filterValue) {
                this.rangeFilter = this.scoreRanges[Number(filterValue)];
            }
            forkJoin([findCourse, findExercise]).subscribe(([courseRes, exerciseRes]) => {
                this.course = courseRes.body!;
                this.exercise = exerciseRes.body!;
                // After both calls are done, the loading flag is removed. If the exercise is not a programming exercise, only the result call is needed.
                zip(this.resultService.getResults(this.exercise), this.loadAndCacheProgrammingExerciseSubmissionState())
                    .pipe(take(1))
                    .subscribe((results) => {
                        this.handleNewResults(results[0]);
                    });

                this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
                this.isAdmin = this.accountService.isAdmin();
            });
        });
    }

    public isPracticeModeAvailable(): boolean {
        switch (this.exercise.type) {
            case ExerciseType.QUIZ:
                const quizExercise: QuizExercise = this.exercise as QuizExercise;
                return quizExercise.isOpenForPractice! && quizExercise.quizEnded!;
            case ExerciseType.PROGRAMMING:
                const programmingExercise: ProgrammingExercise = this.exercise as ProgrammingExercise;
                return dayjs().isAfter(dayjs(programmingExercise.dueDate));
            default:
                return false;
        }
    }

    public isInPracticeMode(): boolean {
        return this.practiceMode;
    }

    public togglePracticeMode(toggle: boolean): void {
        if (this.isPracticeModeAvailable()) {
            this.practiceMode = toggle;
            this.ngOnInit();
        }
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

    private handleNewResults(results: HttpResponse<Result[]>) {
        this.results = results.body || [];
        this.filteredResults = this.filterByScoreRange(this.results.filter((result) => result.participation!['testRun'] === this.practiceMode));
        if (this.exercise.type === ExerciseType.PROGRAMMING) {
            this.profileService.getProfileInfo().subscribe((profileInfo) => {
                setBuildPlanUrlForProgrammingParticipations(
                    profileInfo,
                    this.results.map<ProgrammingExerciseStudentParticipation>((result) => result.participation as ProgrammingExerciseStudentParticipation),
                    (this.exercise as ProgrammingExercise).projectKey,
                );
            });
        }
        this.isLoading = false;
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
            this.handleNewResults(results);
        });
    }

    /**
     * Unsubscribes from all subscriptions
     */
    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.programmingSubmissionService.unsubscribeAllWebsocketTopics(this.exercise);
    }

    formatDate(date: dayjs.Dayjs | Date | undefined) {
        // TODO: we should try to use the artemis date pipe here
        return date ? dayjs(date).format(defaultLongDateTimeFormat) : '';
    }

    /**
     * filters the displayable results based on the given range filter
     * @param results all results for the given exercise
     * @returns results falling into the given score range
     */
    filterByScoreRange(results: Result[]): Result[] {
        if (!this.rangeFilter) {
            return results;
        }
        let filterFunction;
        // If the range to filter against is [90%, 100%], a score of 100% also satisfies this range
        if (this.rangeFilter.upperBound === 100) {
            filterFunction = (result: Result) => !!result.score && result.score >= this.rangeFilter!.lowerBound && result.score <= this.rangeFilter!.upperBound;
        } else {
            // For any other range, the score must be strictly below the upper bound
            filterFunction = (result: Result) => result.score !== undefined && result.score >= this.rangeFilter!.lowerBound && result.score < this.rangeFilter!.upperBound;
        }

        return results.filter(filterFunction);
    }

    /**
     * resets the score range filter
     */
    resetFilterOptions(): void {
        this.rangeFilter = undefined;
        this.filteredResults = this.results;
        this.resultCriteria.filterProp = FilterProp.ALL;
    }
}
