import { Component, ElementRef, OnDestroy, OnInit, QueryList, ViewChildren, inject, viewChild } from '@angular/core';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { Subscription } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { CourseDashboardService } from 'app/core/course/overview/course-dashboard/course-dashboard.service';
import { CompetencyInformation, ExerciseMetrics, StudentMetrics } from 'app/atlas/shared/entities/student-metrics.model';
import { ExerciseLateness } from 'app/core/course/overview/course-dashboard/course-exercise-lateness/course-exercise-lateness.component';
import { ExercisePerformance } from 'app/core/course/overview/course-dashboard/course-exercise-performance/course-exercise-performance.component';
import { round } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { CompetencyAccordionToggleEvent } from 'app/atlas/overview/competency-accordion/competency-accordion.component';
import { CourseChatbotComponent } from 'app/iris/overview/course-chatbot/course-chatbot.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { CourseExercisePerformanceComponent } from './course-exercise-performance/course-exercise-performance.component';
import { CourseExerciseLatenessComponent } from './course-exercise-lateness/course-exercise-lateness.component';
import { CompetencyAccordionComponent } from 'app/atlas/overview/competency-accordion/competency-accordion.component';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';

@Component({
    selector: 'jhi-course-dashboard',
    templateUrl: './course-dashboard.component.html',
    styleUrls: ['./course-dashboard.component.scss'],
    imports: [
        CourseChatbotComponent,
        TranslateDirective,
        NgbProgressbar,
        CourseExercisePerformanceComponent,
        CourseExerciseLatenessComponent,
        CompetencyAccordionComponent,
        FeatureToggleHideDirective,
        FeatureOverlayComponent,
    ],
})
export class CourseDashboardComponent implements OnInit, OnDestroy {
    private courseStorageService = inject(CourseStorageService);
    private alertService = inject(AlertService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private courseDashboardService = inject(CourseDashboardService);
    private profileService = inject(ProfileService);
    private readonly courseChatbot = viewChild('courseChatbot', { read: CourseChatbotComponent });

    courseId: number;
    exerciseId: number;
    points: number = 0;
    maxPoints: number = 0;
    progress: number = 0;
    isLoading = false;
    hasExercises = false;
    hasAvailableExercises = true;
    hasCompetencies = false;
    exerciseLateness?: ExerciseLateness[];
    exercisePerformance?: ExercisePerformance[];
    atlasEnabled = false;
    studentMetrics?: StudentMetrics;
    isCollapsed = false;

    private paramSubscription?: Subscription;
    private courseUpdatesSubscription?: Subscription;
    private metricsSubscription?: Subscription;

    public competencies: CompetencyInformation[] = [];
    public openedAccordionIndex?: number;

    public course?: Course;

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly round = round;

    @ViewChildren('competencyAccordionElement', { read: ElementRef }) competencyAccordions: QueryList<ElementRef>;

    toggleSidebar(): void {
        this.courseChatbot()?.toggleChatHistory();
        this.isCollapsed = !this.isCollapsed;
    }

    ngOnInit(): void {
        this.paramSubscription = this.route?.parent?.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });
        this.setCourse(this.courseStorageService.getCourse(this.courseId));

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.setCourse(course);
        });

        this.atlasEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);
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
                    const lectureUnitMetrics = response.body.lectureUnitStudentMetricsDTO ?? {};

                    // Exercise metrics
                    const exerciseMetrics = response.body.exerciseMetrics ?? {};
                    // Sorted exercises that have a due date in the past
                    let sortedExerciseIds = Object.values(exerciseMetrics?.exerciseInformation ?? {})
                        .filter((exercise) => exercise.dueDate && exercise.dueDate.isBefore(dayjs()))
                        .sort((a, b) => ((a.dueDate ?? a.startDate).isBefore(b.dueDate) ? -1 : 1))
                        .map((exercise) => exercise.id);

                    // Limit the number of exercises to the last 10
                    sortedExerciseIds = sortedExerciseIds.slice(-10);

                    this.hasExercises = sortedExerciseIds.length > 0;
                    this.setOverallPerformance(sortedExerciseIds, exerciseMetrics);
                    this.setExercisePerformance(sortedExerciseIds, exerciseMetrics);
                    this.setExerciseLateness(sortedExerciseIds, exerciseMetrics);

                    // Competency metrics
                    const competencyMetrics = response.body.competencyMetrics ?? {};
                    this.competencies = Object.values(competencyMetrics.competencyInformation ?? {})
                        .filter((competency) => {
                            // Has at least one exercise that has started
                            const exerciseIds = competencyMetrics.exercises?.[competency.id] ?? [];
                            for (const exerciseId of exerciseIds) {
                                const exercise = exerciseMetrics.exerciseInformation?.[exerciseId];
                                if (exercise && exercise.startDate.isBefore(dayjs())) {
                                    return true;
                                }
                            }

                            // Or has at least one lecture unit that has been released
                            const lectureUnitIds = competencyMetrics.lectureUnits?.[competency.id] ?? [];
                            for (const lectureUnitId of lectureUnitIds) {
                                const lectureUnit = lectureUnitMetrics.lectureUnitInformation?.[lectureUnitId];
                                if (lectureUnit && lectureUnit.releaseDate && lectureUnit.releaseDate.isBefore(dayjs())) {
                                    return true;
                                }
                            }
                        })
                        .sort((a, b) => {
                            return a.id < b.id ? -1 : 1;
                        });

                    this.hasCompetencies = this.competencies.length > 0;
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
     * @param exerciseIds - An array of relevant exercise IDs
     * @param exerciseMetrics - An object containing metrics related to exercises.
     */
    private setOverallPerformance(exerciseIds: number[], exerciseMetrics: ExerciseMetrics) {
        const relevantExercises = Object.values(exerciseMetrics?.exerciseInformation ?? {}).filter((exercise) => exerciseIds.includes(exercise.id));
        const points = relevantExercises.reduce((sum, exercise) => sum + ((exerciseMetrics.score?.[exercise.id] || 0) / 100) * exercise.maxPoints, 0);
        this.points = round(points, 1);

        const maxPoints = relevantExercises.reduce((sum, exercise) => sum + exercise.maxPoints, 0);
        this.maxPoints = round(maxPoints, 1);
        this.progress = round((points / maxPoints) * 100, 1);
    }

    /**
     * This method sets the exercise performance data for the course dashboard from the given exercise metrics.
     *
     * @param sortedExerciseIds - An array of exercise IDs sorted in a specific order.
     * @param exerciseMetrics - An object containing metrics related to exercises.
     */
    private setExercisePerformance(sortedExerciseIds: number[], exerciseMetrics: ExerciseMetrics) {
        this.exercisePerformance = sortedExerciseIds.flatMap((exerciseId) => {
            const exerciseInformation = exerciseMetrics?.exerciseInformation?.[exerciseId];
            return exerciseInformation
                ? [
                      {
                          exerciseId: exerciseId,
                          title: exerciseInformation.title,
                          shortName: exerciseInformation.shortName,
                          score: exerciseMetrics.score?.[exerciseId],
                          averageScore: exerciseMetrics.averageScore?.[exerciseId],
                      },
                  ]
                : [];
        });
    }

    /**
     * This method sets the exercise lateness data for the course dashboard from the given exercise metrics.
     *
     * @param sortedExerciseIds - An array of exercise IDs sorted in a specific order.
     * @param exerciseMetrics - An object containing metrics related to exercises.
     */
    private setExerciseLateness(sortedExerciseIds: number[], exerciseMetrics: ExerciseMetrics) {
        this.exerciseLateness = sortedExerciseIds.flatMap((exerciseId) => {
            const exerciseInformation = exerciseMetrics?.exerciseInformation?.[exerciseId];
            return exerciseInformation
                ? [
                      {
                          exerciseId: exerciseId,
                          title: exerciseInformation.title,
                          shortName: exerciseInformation.shortName,
                          relativeLatestSubmission: exerciseMetrics.latestSubmission?.[exerciseId],
                          relativeAverageLatestSubmission: exerciseMetrics.averageLatestSubmission?.[exerciseId],
                      },
                  ]
                : [];
        });
    }

    private setCourse(course?: Course) {
        const shouldLoadMetrics = this.course?.id !== course?.id && course?.studentCourseAnalyticsDashboardEnabled;
        this.course = course;
        this.hasAvailableExercises = course?.exercises ? course.exercises.length > 0 : true;
        if (this.course && shouldLoadMetrics) {
            this.loadMetrics();
        }
    }

    handleToggle(event: CompetencyAccordionToggleEvent) {
        this.openedAccordionIndex = event.opened ? event.index : undefined;
    }

    navigateToLearningPaths() {
        this.router.navigate(['courses', this.courseId, 'learning-path']);
    }
}
