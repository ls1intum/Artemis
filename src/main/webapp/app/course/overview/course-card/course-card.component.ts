import { NgClass } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ChartModule } from 'primeng/chart';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { singleSeriesChartData } from 'app/shared-ui/chart/chart-adapters';
import { doughnutChartOptions } from 'app/shared-ui/chart/chart-options';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { ScoreType } from 'app/foundation/constants/score-type.constants';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { CourseCardHeaderComponent } from 'app/course/overview/course-card-header/course-card-header.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { CourseScores } from 'app/course/manage/course-scores/course-scores';
import { CourseNotificationService } from 'app/notification/course-notification/course-notification.service';
import { filter, switchMap } from 'rxjs';

@Component({
    selector: 'jhi-overview-course-card',
    templateUrl: './course-card.component.html',
    styleUrls: ['course-card.scss'],
    imports: [CourseCardHeaderComponent, ChartModule, NgClass, TranslateDirective, RouterLink, FontAwesomeModule],
})
export class CourseCardComponent {
    private router = inject(Router);
    private translateService = inject(TranslateService);
    private scoresStorageService = inject(ScoresStorageService);
    private exerciseService = inject(ExerciseService);
    private courseNotificationService = inject(CourseNotificationService);
    private destroyRef = inject(DestroyRef);

    protected readonly faArrowRight = faArrowRight;

    constructor() {
        effect(() => {
            const course = this.course();
            untracked(() => {
                if (!course) {
                    return;
                }
                this.processCourseData(course);
            });
        });

        // Use toObservable + switchMap to automatically cancel previous subscriptions
        toObservable(this.course)
            .pipe(
                filter((course): course is Course => !!course?.id),
                switchMap((course) => this.courseNotificationService.getNotificationCountForCourse$(course.id!)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe((count) => {
                this._courseNotificationCount.set(count);
            });
    }

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    readonly course = input.required<Course>();

    private readonly _nextRelevantExercise = signal<Exercise | undefined>(undefined);
    private readonly _exerciseCount = signal(0);
    private readonly _totalRelativeScore = signal<number>(0);
    private readonly _totalReachableScore = signal<number>(0);
    private readonly _totalAbsoluteScore = signal<number>(0);
    private readonly _courseNotificationCount = signal(0);
    private readonly _doughnutChartEntries = signal<ChartSeriesEntry[]>([
        { name: 'achievedPointsLabel', value: 0 },
        { name: 'missingPointsLabel', value: 0 },
    ]);

    readonly nextRelevantExercise = this._nextRelevantExercise.asReadonly();
    readonly exerciseCount = this._exerciseCount.asReadonly();
    readonly totalRelativeScore = this._totalRelativeScore.asReadonly();
    readonly totalReachableScore = this._totalReachableScore.asReadonly();
    readonly totalAbsoluteScore = this._totalAbsoluteScore.asReadonly();
    readonly courseNotificationCount = this._courseNotificationCount.asReadonly();
    readonly doughnutChartEntries = this._doughnutChartEntries.asReadonly();

    private readonly chartColors = inject(ChartColorService).resolvedColors(() => [GraphColors.GREEN, GraphColors.RED]);

    readonly chartData = computed(() => singleSeriesChartData(this.doughnutChartEntries(), this.chartColors()));
    readonly chartOptions = computed(() =>
        doughnutChartOptions({
            arcWidth: 0.3,
            legend: false,
            tooltip: {
                title: (items) => {
                    const label = items[0]?.label;
                    return label ? this.translateService.instant('artemisApp.courseOverview.statistics.' + label) : '';
                },
                label: (item) => `${item.parsed}`,
            },
        }),
    );

    private processCourseData(course: Course): void {
        if (course.exercises && course.exercises.length > 0) {
            this._exerciseCount.set(course.exercises.length);

            const nextExercisesWithAnyScore = this.exerciseService.getNextExercisesForDays(course.exercises);
            // filters out every already successful (100%) exercise, only exercises left that still need work
            const nextExercises = nextExercisesWithAnyScore.filter((exercise: Exercise) => !exercise.studentParticipations?.[0]?.submissions?.[0]?.results?.[0]?.successful);

            if (nextExercises.length > 0 && nextExercises[0]) {
                this._nextRelevantExercise.set(nextExercises[0]);
            }

            const totalScoresForCourse: CourseScores | undefined = this.scoresStorageService.getStoredTotalScores(course.id!);
            if (totalScoresForCourse) {
                this._totalRelativeScore.set(totalScoresForCourse.studentScores[ScoreType.CURRENT_RELATIVE_SCORE]);
                this._totalAbsoluteScore.set(totalScoresForCourse.studentScores[ScoreType.ABSOLUTE_SCORE]);
                this._totalReachableScore.set(totalScoresForCourse[ScoreType.REACHABLE_POINTS]);
            }

            // Adjust for bonus points, i.e. when the student has achieved more than is reachable
            const scoreNotReached = roundValueSpecifiedByCourseSettings(Math.max(0, this._totalReachableScore() - this._totalAbsoluteScore()), course);
            this._doughnutChartEntries.set([
                { name: 'achievedPointsLabel', value: this._totalAbsoluteScore() },
                { name: 'missingPointsLabel', value: scoreNotReached },
            ]);
        }
    }

    /**
     * Delegates the user to the corresponding course page when clicking on the chart
     */
    onSelect(): void {
        this.router.navigate(['courses', this.course().id]);
    }
}
