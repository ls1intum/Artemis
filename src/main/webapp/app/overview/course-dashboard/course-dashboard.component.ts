import { Component, OnDestroy, OnInit } from '@angular/core';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Subscription, forkJoin } from 'rxjs';
import { Exercise } from 'app/entities/exercise.model';
import { ActivatedRoute } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Competency } from 'app/entities/competency.model';
import { onError } from 'app/shared/util/global.utils';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseDashboardService } from 'app/overview/course-dashboard/course-dashboard.service';
import { ExerciseMetrics } from 'app/entities/student-metrics.model';
import { ExerciseLateness } from 'app/overview/course-dashboard/course-exercise-lateness/course-exercise-lateness.component';
import { ExercisePerformance } from 'app/overview/course-dashboard/course-exercise-performance/course-exercise-performance.component';

@Component({
    selector: 'jhi-course-dashboard',
    templateUrl: './course-dashboard.component.html',
})
export class CourseDashboardComponent implements OnInit, OnDestroy {
    courseId: number;
    exerciseId: number;
    isLoading = false;

    public competencies: Competency[] = [];
    private prerequisites: Competency[] = [];

    private paramSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;
    private courseExercises: Exercise[] = [];
    public course?: Course;
    public data: any;

    metricsSubscription?: Subscription;

    public exerciseLateness: ExerciseLateness[] = [];
    public exercisePerformance: ExercisePerformance[] = [];

    constructor(
        private courseStorageService: CourseStorageService,
        private alertService: AlertService,
        private route: ActivatedRoute,
        private competencyService: CompetencyService,
        private courseDashboardService: CourseDashboardService,
    ) {}

    ngOnInit(): void {
        this.paramSubscription = this.route.parent?.parent?.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });
        this.setCourse(this.courseStorageService.getCourse(this.courseId));

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.setCourse(course);
        });
    }

    private onCourseLoad(): void {
        if (this.course?.exercises) {
            this.courseExercises = this.course.exercises;
        }
    }

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.courseUpdatesSubscription?.unsubscribe();
        this.metricsSubscription?.unsubscribe();
    }

    /**
     * Loads all prerequisites and competencies for the course
     */
    loadCompetencies() {
        this.isLoading = true;
        forkJoin([this.competencyService.getAllForCourse(this.courseId), this.competencyService.getAllPrerequisitesForCourse(this.courseId)]).subscribe({
            next: ([competencies, prerequisites]) => {
                this.competencies = competencies.body!;
                this.prerequisites = prerequisites.body!;
                // Also update the course, so we do not need to fetch again next time
                if (this.course) {
                    this.course.competencies = this.competencies;
                    this.course.prerequisites = this.prerequisites;
                }
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
        });
    }

    /**
     * Loads the metrics for the course
     */
    loadMetrics() {
        if (this.metricsSubscription) {
            this.metricsSubscription.unsubscribe();
        }

        this.isLoading = true;
        this.metricsSubscription = this.courseDashboardService.getCourseMetricsForUser(this.courseId).subscribe({
            next: (response) => {
                if (response.body && response.body.exerciseMetrics) {
                    const exerciseMetrics = response.body.exerciseMetrics;
                    const sortedExerciseIds = Object.values(exerciseMetrics.exerciseInformation)
                        .sort((a, b) => new Date(a.due).getTime() - new Date(b.start).getTime())
                        .map((exercise) => exercise.id);

                    this.setExercisePerformance(sortedExerciseIds, exerciseMetrics);
                    this.setExerciseLateness(sortedExerciseIds, exerciseMetrics);
                }
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
                this.isLoading = false;
            },
        });
    }

    private setExercisePerformance(sortedExerciseIds: number[], exerciseMetrics: ExerciseMetrics) {
        this.exercisePerformance = sortedExerciseIds.map((exerciseId) => {
            const exerciseInformation = exerciseMetrics.exerciseInformation[exerciseId];
            return {
                exerciseId: exerciseId,
                title: exerciseInformation.title,
                shortName: exerciseInformation.shortName,
                score: exerciseMetrics.score?.[exerciseId] || 0,
                averageScore: exerciseMetrics.averageScore[exerciseId],
            };
        });
        console.log(this.exercisePerformance);
    }

    private setExerciseLateness(sortedExerciseIds: number[], exerciseMetrics: ExerciseMetrics) {
        this.exerciseLateness = sortedExerciseIds.map((exerciseId) => {
            const exerciseInformation = exerciseMetrics.exerciseInformation[exerciseId];
            return {
                exerciseId: exerciseId,
                title: exerciseInformation.title,
                shortName: exerciseInformation.shortName,
                relativeLatestSubmission: exerciseMetrics.latestSubmission[exerciseId],
                relativeAverageLatestSubmission: exerciseMetrics.averageLatestSubmission[exerciseId],
            };
        });
        console.log(this.exerciseLateness);
    }

    private setCourse(course?: Course) {
        this.course = course;
        this.onCourseLoad();
        // Note: this component is only shown if there are at least 1 competencies or at least 1 prerequisites, so if they do not exist, we load the data from the server
        if (this.course && ((this.course.competencies && this.course.competencies.length > 0) || (this.course.prerequisites && this.course.prerequisites.length > 0))) {
            this.competencies = this.course.competencies || [];
            this.prerequisites = this.course.prerequisites || [];
        } else {
            this.loadCompetencies();
        }

        if (this.course) {
            this.loadMetrics();
        }
    }

    protected readonly FeatureToggle = FeatureToggle;
}
