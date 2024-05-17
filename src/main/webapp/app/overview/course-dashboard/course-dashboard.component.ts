import { Component, ElementRef, OnDestroy, OnInit, QueryList, ViewChildren } from '@angular/core';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseDashboardService } from 'app/overview/course-dashboard/course-dashboard.service';
import { CompetencyInformation, ExerciseMetrics, StudentMetrics } from 'app/entities/student-metrics.model';
import { ExerciseLateness } from 'app/overview/course-dashboard/course-exercise-lateness/course-exercise-lateness.component';
import { ExercisePerformance } from 'app/overview/course-dashboard/course-exercise-performance/course-exercise-performance.component';
import { ICompetencyAccordionToggleEvent } from 'app/shared/competency/interfaces/competency-accordion-toggle-event.interface';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-course-dashboard',
    templateUrl: './course-dashboard.component.html',
    styleUrls: ['./course-dashboard.component.scss'],
})
export class CourseDashboardComponent implements OnInit, OnDestroy {
    courseId: number;
    exerciseId: number;
    points: number = 0;
    maxPoints: number = 0;
    isLoading = false;
    hasExercises = false;
    hasCompetencies = false;
    exerciseLateness?: ExerciseLateness[];
    exercisePerformance?: ExercisePerformance[];
    studentMetrics?: StudentMetrics;

    private paramSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;
    private metricsSubscription?: Subscription;

    public competencies: CompetencyInformation[] = [];
    public openedAccordionIndex?: number;

    public course?: Course;

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly round = round;

    @ViewChildren('competencyAccordionElement', { read: ElementRef }) competencyAccordions: QueryList<ElementRef>;

    constructor(
        private courseStorageService: CourseStorageService,
        private alertService: AlertService,
        private route: ActivatedRoute,
        private router: Router,
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

    ngOnDestroy(): void {
        this.paramSubscription?.unsubscribe();
        this.courseUpdatesSubscription?.unsubscribe();
        this.metricsSubscription?.unsubscribe();
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
                if (response.body) {
                    this.studentMetrics = response.body;
                    if (response.body.exerciseMetrics) {
                        const exerciseMetrics = response.body.exerciseMetrics;
                        const sortedExerciseIds = Object.values(exerciseMetrics.exerciseInformation)
                            .sort((a, b) => (a.dueDate.isBefore(b.dueDate) ? -1 : 1))
                            .map((exercise) => exercise.id);

                        this.hasExercises = sortedExerciseIds.length > 0;
                        this.setOverallPerformance(exerciseMetrics);
                        this.setExercisePerformance(sortedExerciseIds, exerciseMetrics);
                        this.setExerciseLateness(sortedExerciseIds, exerciseMetrics);
                    }
                    if (response.body.competencyMetrics) {
                        this.competencies = Object.values(response.body.competencyMetrics.competencyInformation).sort((a, b) => {
                            const aDate = a.softDueDate ? new Date(a.softDueDate).getTime() : 0;
                            const bDate = b.softDueDate ? new Date(b.softDueDate).getTime() : 0;
                            return aDate - bDate;
                        });
                        this.hasCompetencies = this.competencies.length > 0;
                    }
                }
                this.isLoading = false;
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
                this.isLoading = false;
            },
        });
    }

    /**
     * This method sets the overall performance, i.e. the points and max points.
     *
     * @param {ExerciseMetrics} exerciseMetrics - An object containing metrics related to exercises.
     */
    private setOverallPerformance(exerciseMetrics: ExerciseMetrics) {
        const points = Object.values(exerciseMetrics.exerciseInformation).reduce(
            (sum, exercise) => sum + ((exerciseMetrics.score?.[exercise.id] || 0) / 100) * exercise.maxPoints,
            0,
        );
        this.points = round(points, 1);

        const maxPoints = Object.values(exerciseMetrics.exerciseInformation).reduce((sum, exercise) => sum + exercise.maxPoints, 0);
        this.maxPoints = round(maxPoints, 1);
    }

    /**
     * This method sets the exercise performance data for the course dashboard from the given exercise metrics.
     *
     * @param {number[]} sortedExerciseIds - An array of exercise IDs sorted in a specific order.
     * @param {ExerciseMetrics} exerciseMetrics - An object containing metrics related to exercises.
     */
    private setExercisePerformance(sortedExerciseIds: number[], exerciseMetrics: ExerciseMetrics) {
        this.exercisePerformance = sortedExerciseIds.map((exerciseId) => {
            const exerciseInformation = exerciseMetrics.exerciseInformation[exerciseId];
            return {
                exerciseId: exerciseId,
                title: exerciseInformation.title,
                shortName: exerciseInformation.shortName,
                score: exerciseMetrics.score?.[exerciseId],
                averageScore: exerciseMetrics.averageScore?.[exerciseId],
            };
        });
    }

    /**
     * This method sets the exercise lateness data for the course dashboard from the given exercise metrics.
     *
     * @param {number[]} sortedExerciseIds - An array of exercise IDs sorted in a specific order.
     * @param {ExerciseMetrics} exerciseMetrics - An object containing metrics related to exercises.
     */
    private setExerciseLateness(sortedExerciseIds: number[], exerciseMetrics: ExerciseMetrics) {
        this.exerciseLateness = sortedExerciseIds.map((exerciseId) => {
            const exerciseInformation = exerciseMetrics.exerciseInformation[exerciseId];
            return {
                exerciseId: exerciseId,
                title: exerciseInformation.title,
                shortName: exerciseInformation.shortName,
                relativeLatestSubmission: exerciseMetrics.latestSubmission?.[exerciseId],
                relativeAverageLatestSubmission: exerciseMetrics.averageLatestSubmission?.[exerciseId],
            };
        });
    }

    private setCourse(course?: Course) {
        this.course = course;
        if (this.course) {
            this.loadMetrics();
        }
    }

    handleToggle(event: ICompetencyAccordionToggleEvent) {
        this.openedAccordionIndex = event.opened ? event.index : undefined;
    }

    get learningPathsEnabled() {
        return this.course?.learningPathsEnabled || false;
    }

    navigateToLearningPaths() {
        this.router.navigate(['courses', this.courseId, 'learning-path']);
    }
}
