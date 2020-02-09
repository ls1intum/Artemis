import { JhiAlertService } from 'ng-jhipster';
import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { Subscription } from 'rxjs/Subscription';
import { ActivatedRoute } from '@angular/router';
import { DifferencePipe } from 'ngx-moment';
import { HttpResponse } from '@angular/common/http';
import { Moment } from 'moment';
import { areManualResultsAllowed, Exercise, ExerciseService, ExerciseType } from 'app/entities/exercise';
import { Course } from 'app/entities/course';
import { CourseService } from 'app/entities/course/course.service';
import { Result, ResultService } from 'app/entities/result';
import { SourceTreeService } from 'app/components/util/sourceTree.service';
import { ModelingAssessmentService } from 'app/entities/modeling-assessment';
import { ProgrammingExerciseStudentParticipation, StudentParticipation } from 'app/entities/participation';
import { take, tap } from 'rxjs/operators';
import { of, zip } from 'rxjs';
import { AssessmentType } from 'app/entities/assessment-type';
import { FeatureToggle } from 'app/feature-toggle';
import { ProgrammingSubmissionService } from 'app/programming-submission/programming-submission.service';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { SubmissionExerciseType } from 'app/entities/submission';
import { ProgrammingSubmission } from 'app/entities/programming-submission';

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
    providers: [JhiAlertService, ModelingAssessmentService, SourceTreeService],
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

    constructor(
        private route: ActivatedRoute,
        private momentDiff: DifferencePipe,
        private courseService: CourseService,
        private exerciseService: ExerciseService,
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

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            this.isLoading = true;
            this.courseService.find(params['courseId']).subscribe((res: HttpResponse<Course>) => {
                this.course = res.body!;
            });
            this.exerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<Exercise>) => {
                this.exercise = res.body!;
                // After both calls are done, the loading flag is removed. If the exercise is not a programming exercise, only the result call is needed.
                zip(this.getResults(), this.loadAndCacheProgrammingExerciseSubmissionState())
                    .pipe(take(1))
                    .subscribe(() => (this.isLoading = false));
                this.newManualResultAllowed = areManualResultsAllowed(this.exercise);
            });
        });
    }

    /**
     * We need to preload the pending submissions here, otherwise every updating-result would trigger a single REST call.
     * Will return immediately if the exercise is not of type PROGRAMMING.
     */
    private loadAndCacheProgrammingExerciseSubmissionState() {
        // TODO: this is deactivated because of performance reasons as it would lead quickly to thousands of subscribed websocket topics
        // return this.exercise.type === ExerciseType.PROGRAMMING ? this.programmingSubmissionService.getSubmissionStateOfExercise(this.exercise.id) : of(null);
        return of(null);
    }

    getResults() {
        return this.resultService
            .getResultsForExercise(this.exercise.id, {
                withSubmissions: this.exercise.type === ExerciseType.MODELING,
            })
            .pipe(
                tap((res: HttpResponse<Result[]>) => {
                    this.results = res.body!.map(result => {
                        result.participation!.results = [result];
                        (result.participation! as StudentParticipation).exercise = this.exercise;
                        result.durationInMinutes = this.durationInMinutes(
                            result.completionDate!,
                            result.participation!.initializationDate ? result.participation!.initializationDate : this.exercise.releaseDate!,
                        );
                        // Nest submission into participation so that it is available for the result component
                        if (result.participation && result.submission) {
                            result.participation.submissions = [result.submission];
                        }
                        return result;
                    });
                }),
            );
    }

    updateResultFilter(newValue: FilterProp) {
        this.isLoading = true;
        setTimeout(() => {
            this.resultCriteria.filterProp = newValue;
            this.isLoading = false;
        });
    }

    filterResultByProp = (result: Result) => {
        switch (this.resultCriteria.filterProp) {
            case FilterProp.SUCCESSFUL:
                return result.successful;
            case FilterProp.UNSUCCESSFUL:
                return !result.successful;
            case FilterProp.BUILD_FAILED:
                return (
                    result.submission && result.submission.submissionExerciseType === SubmissionExerciseType.PROGRAMMING && (result.submission as ProgrammingSubmission).buildFailed
                );
            case FilterProp.MANUAL:
                return result.assessmentType === AssessmentType.MANUAL;
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

    durationInMinutes(completionDate: Moment, initializationDate: Moment) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }

    buildPlanId(result: Result): string {
        return (result.participation! as ProgrammingExerciseStudentParticipation).buildPlanId;
    }

    projectKey(): string {
        return (this.exercise as ProgrammingExercise).projectKey!;
    }

    goToRepository(result: Result) {
        window.open((result.participation! as ProgrammingExerciseStudentParticipation).repositoryUrl);
    }

    exportNames() {
        if (this.results.length > 0) {
            const rows: string[] = [];
            this.results.forEach((result, index) => {
                const studentParticipation = result.participation! as StudentParticipation;
                const studentName = studentParticipation.student.name!;
                rows.push(index === 0 ? 'data:text/csv;charset=utf-8,' + studentName : studentName);
            });
            const csvContent = rows.join('\n');
            const encodedUri = encodeURI(csvContent);
            const link = document.createElement('a');
            link.setAttribute('href', encodedUri);
            link.setAttribute('download', 'results-names.csv');
            document.body.appendChild(link); // Required for FF
            link.click();
        }
    }

    exportResults() {
        if (this.results.length > 0) {
            const rows: string[] = [];
            this.results.forEach((result, index) => {
                const studentParticipation = result.participation! as StudentParticipation;
                const studentName = studentParticipation.student.name!;
                const studentId = studentParticipation.student.login;
                const score = result.score;

                if (index === 0) {
                    if (this.exercise.type !== ExerciseType.PROGRAMMING) {
                        rows.push('data:text/csv;charset=utf-8,Name, Username, Score');
                    } else {
                        rows.push('data:text/csv;charset=utf-8,Name, Username, Score, Repo Link');
                    }
                }
                if (this.exercise.type !== ExerciseType.PROGRAMMING) {
                    rows.push(studentName + ', ' + studentId + ', ' + score);
                } else {
                    const repoLink = (studentParticipation as ProgrammingExerciseStudentParticipation).repositoryUrl;
                    rows.push(studentName + ', ' + studentId + ', ' + score + ', ' + repoLink);
                }
            });
            const csvContent = rows.join('\n');
            const encodedUri = encodeURI(csvContent);
            const link = document.createElement('a');
            link.setAttribute('href', encodedUri);
            link.setAttribute('download', 'results-scores.csv');
            document.body.appendChild(link); // Required for FF
            link.click();
        }
    }

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param result
     */
    searchResultFormatter = (result: Result) => {
        const login = (result.participation as StudentParticipation).student.login;
        const name = (result.participation as StudentParticipation).student.name;
        return `${login} (${name})`;
    };

    /**
     * Converts a result object to a string that can be searched for. This is
     * used by the autocomplete select inside the data table.
     *
     * @param result
     */
    searchTextFromResult = (result: Result): string => {
        return (result.participation as StudentParticipation).student.login || '';
    };

    refresh() {
        this.isLoading = true;
        this.results = [];
        this.getResults().subscribe(() => (this.isLoading = false));
    }

    ngOnDestroy() {
        this.paramSub.unsubscribe();
        this.programmingSubmissionService.unsubscribeAllWebsocketTopics(this.exercise);
    }
}
