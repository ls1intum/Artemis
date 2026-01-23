import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Color, NgxChartsModule, PieChartModule, ScaleType } from '@swimlane/ngx-charts';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { ScoreType } from 'app/shared/constants/score-type.constants';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { CourseCardHeaderComponent } from 'app/core/course/overview/course-card-header/course-card-header.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ScoresStorageService } from 'app/core/course/manage/course-scores/scores-storage.service';
import { CourseScores } from 'app/core/course/manage/course-scores/course-scores';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';

@Component({
    selector: 'jhi-overview-course-card',
    templateUrl: './course-card.component.html',
    styleUrls: ['course-card.scss'],
    imports: [CourseCardHeaderComponent, NgxChartsModule, PieChartModule, TranslateDirective, RouterLink, FontAwesomeModule],
})
export class CourseCardComponent {
    private router = inject(Router);
    private scoresStorageService = inject(ScoresStorageService);
    private exerciseService = inject(ExerciseService);
    private courseNotificationService = inject(CourseNotificationService);
    private destroyRef = inject(DestroyRef);

    protected readonly faArrowRight = faArrowRight;

    constructor() {
        effect(() => {
            const course = this.course();
            untracked(() => {
                this.processCourseData(course);
                this.subscribeToNotificationCount(course);
            });
        });
    }

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    readonly course = input<Course>(undefined!);

    private readonly _nextRelevantExercise = signal<Exercise | undefined>(undefined);
    private readonly _exerciseCount = signal(0);
    private readonly _totalRelativeScore = signal<number>(0);
    private readonly _totalReachableScore = signal<number>(0);
    private readonly _totalAbsoluteScore = signal<number>(0);
    private readonly _courseNotificationCount = signal(0);
    private readonly _ngxDoughnutData = signal<any[]>([
        { name: 'achievedPointsLabel', value: 0 },
        { name: 'missingPointsLabel', value: 0 },
    ]);

    readonly nextRelevantExercise = computed(() => this._nextRelevantExercise());
    readonly exerciseCount = computed(() => this._exerciseCount());
    readonly totalRelativeScore = computed(() => this._totalRelativeScore());
    readonly totalReachableScore = computed(() => this._totalReachableScore());
    readonly totalAbsoluteScore = computed(() => this._totalAbsoluteScore());
    readonly courseNotificationCount = computed(() => this._courseNotificationCount());
    readonly ngxDoughnutData = computed(() => this._ngxDoughnutData());

    ngxColor = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.GREEN, GraphColors.RED],
    } as Color;

    private subscribeToNotificationCount(course: Course): void {
        if (course.id) {
            this.courseNotificationService
                .getNotificationCountForCourse$(course.id!)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe((count) => {
                    this._courseNotificationCount.set(count);
                });
        }
    }

    private processCourseData(course: Course): void {
        if (course.exercises && course.exercises.length > 0) {
            this._exerciseCount.set(course.exercises.length);

            const nextExercisesWithAnyScore = this.exerciseService.getNextExercisesForDays(course.exercises!);
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
            this._ngxDoughnutData.set([
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
