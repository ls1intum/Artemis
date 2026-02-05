import { Component, DestroyRef, ElementRef, OnDestroy, computed, inject, signal, viewChildren } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { Subscription, switchMap, tap } from 'rxjs';
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
export class CourseDashboardComponent implements OnDestroy {
    private courseStorageService = inject(CourseStorageService);
    private alertService = inject(AlertService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private courseDashboardService = inject(CourseDashboardService);
    private profileService = inject(ProfileService);
    private destroyRef = inject(DestroyRef);

    private readonly _courseId = signal<number>(0);
    private readonly _points = signal(0);
    private readonly _maxPoints = signal(0);
    private readonly _progress = signal(0);
    private readonly _isLoading = signal(false);
    private readonly _hasExercises = signal(false);
    private readonly _hasCompetencies = signal(false);
    private readonly _exerciseLateness = signal<ExerciseLateness[] | undefined>(undefined);
    private readonly _exercisePerformance = signal<ExercisePerformance[] | undefined>(undefined);
    private readonly _atlasEnabled = signal(false);
    private readonly _studentMetrics = signal<StudentMetrics | undefined>(undefined);
    private readonly _competencies = signal<CompetencyInformation[]>([]);
    private readonly _openedAccordionIndex = signal<number | undefined>(undefined);
    private readonly _course = signal<Course | undefined>(undefined);

    readonly courseId = computed(() => this._courseId());
    readonly points = computed(() => this._points());
    readonly maxPoints = computed(() => this._maxPoints());
    readonly progress = computed(() => this._progress());
    readonly isLoading = computed(() => this._isLoading());
    readonly hasExercises = computed(() => this._hasExercises());
    readonly hasCompetencies = computed(() => this._hasCompetencies());
    readonly exerciseLateness = computed(() => this._exerciseLateness());
    readonly exercisePerformance = computed(() => this._exercisePerformance());
    readonly atlasEnabled = computed(() => this._atlasEnabled());
    readonly studentMetrics = computed(() => this._studentMetrics());
    readonly competencies = computed(() => this._competencies());
    readonly openedAccordionIndex = computed(() => this._openedAccordionIndex());
    readonly course = computed(() => this._course());

    private metricsSubscription?: Subscription;

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly round = round;

    readonly competencyAccordions = viewChildren('competencyAccordionElement', { read: ElementRef });

    constructor() {
        this.route?.parent?.params
            .pipe(
                tap((params) => {
                    this._courseId.set(parseInt(params['courseId'], 10));
                    this.setCourse(this.courseStorageService.getCourse(this._courseId()));
                }),
                switchMap(() => this.courseStorageService.subscribeToCourseUpdates(this._courseId())),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((course: Course) => {
                this.setCourse(course);
            });

        this._atlasEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS));
    }

    /**
     * Loads the metrics for the course
     */
    loadMetrics() {
        if (this.metricsSubscription) {
            this.metricsSubscription.unsubscribe();
        }

        this._isLoading.set(true);
        this.metricsSubscription = this.courseDashboardService.getCourseMetricsForUser(this._courseId()).subscribe({
            next: (response) => {
                if (response.body) {
                    this._studentMetrics.set(response.body);
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

                    this._hasExercises.set(sortedExerciseIds.length > 0);
                    this.setOverallPerformance(sortedExerciseIds, exerciseMetrics);
                    this.setExercisePerformance(sortedExerciseIds, exerciseMetrics);
                    this.setExerciseLateness(sortedExerciseIds, exerciseMetrics);

                    // Competency metrics
                    const competencyMetrics = response.body.competencyMetrics ?? {};
                    const filteredCompetencies = Object.values(competencyMetrics.competencyInformation ?? {})
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

                    this._competencies.set(filteredCompetencies);
                    this._hasCompetencies.set(filteredCompetencies.length > 0);
                }
                this._isLoading.set(false);
            },
            error: (errorResponse: HttpErrorResponse) => {
                onError(this.alertService, errorResponse);
                this._isLoading.set(false);
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
        this._points.set(round(points, 1));

        const maxPoints = relevantExercises.reduce((sum, exercise) => sum + exercise.maxPoints, 0);
        this._maxPoints.set(round(maxPoints, 1));
        this._progress.set(round((points / maxPoints) * 100, 1));
    }

    /**
     * This method sets the exercise performance data for the course dashboard from the given exercise metrics.
     *
     * @param sortedExerciseIds - An array of exercise IDs sorted in a specific order.
     * @param exerciseMetrics - An object containing metrics related to exercises.
     */
    private setExercisePerformance(sortedExerciseIds: number[], exerciseMetrics: ExerciseMetrics) {
        this._exercisePerformance.set(
            sortedExerciseIds.flatMap((exerciseId) => {
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
            }),
        );
    }

    /**
     * This method sets the exercise lateness data for the course dashboard from the given exercise metrics.
     *
     * @param sortedExerciseIds - An array of exercise IDs sorted in a specific order.
     * @param exerciseMetrics - An object containing metrics related to exercises.
     */
    private setExerciseLateness(sortedExerciseIds: number[], exerciseMetrics: ExerciseMetrics) {
        this._exerciseLateness.set(
            sortedExerciseIds.flatMap((exerciseId) => {
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
            }),
        );
    }

    private setCourse(course?: Course) {
        const shouldLoadMetrics = this._course()?.id !== course?.id && course?.studentCourseAnalyticsDashboardEnabled;
        this._course.set(course);
        if (course && shouldLoadMetrics) {
            this.loadMetrics();
        }
    }

    handleToggle(event: CompetencyAccordionToggleEvent) {
        this._openedAccordionIndex.set(event.opened ? event.index : undefined);
    }

    ngOnDestroy() {
        this.metricsSubscription?.unsubscribe();
    }

    navigateToLearningPaths() {
        this.router.navigate(['courses', this._courseId(), 'learning-path']);
    }
}
