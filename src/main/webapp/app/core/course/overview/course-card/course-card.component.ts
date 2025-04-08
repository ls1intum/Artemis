import { Component, Input, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Color, NgxChartsModule, PieChartModule, ScaleType } from '@swimlane/ngx-charts';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { ScoreType } from 'app/shared/constants/score-type.constants';
import { faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { CourseCardHeaderComponent } from 'app/core/course/overview/course-card-header/course-card-header.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ScoresStorageService } from 'app/core/course/manage/course-scores/scores-storage.service';
import { CourseScores } from 'app/core/course/manage/course-scores/course-scores';
import { Subscription } from 'rxjs';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';

@Component({
    selector: 'jhi-overview-course-card',
    templateUrl: './course-card.component.html',
    styleUrls: ['course-card.scss'],
    imports: [CourseCardHeaderComponent, NgxChartsModule, PieChartModule, TranslateDirective, RouterLink, FontAwesomeModule],
})
export class CourseCardComponent implements OnChanges, OnInit, OnDestroy {
    private router = inject(Router);
    private scoresStorageService = inject(ScoresStorageService);
    private exerciseService = inject(ExerciseService);
    private courseNotificationService = inject(CourseNotificationService);

    protected readonly faArrowRight = faArrowRight;

    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    @Input() course: Course;
    @Input() hasGuidedTour: boolean;

    CachingStrategy = CachingStrategy;

    nextRelevantExercise?: Exercise;
    exerciseCount = 0;

    totalRelativeScore: number;
    totalReachableScore: number;
    totalAbsoluteScore: number;
    courseNotificationCount: number = 0;
    private courseNotificationCountSubscription?: Subscription;

    // ngx
    ngxDoughnutData: any[] = [
        { name: 'achievedPointsLabel', value: 0 },
        { name: 'missingPointsLabel', value: 0 },
    ];
    ngxColor = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.GREEN, GraphColors.RED],
    } as Color;

    ngOnInit(): void {
        if (this.course.id) {
            this.courseNotificationCountSubscription = this.courseNotificationService.getNotificationCountForCourse$(this.course.id!).subscribe((count) => {
                this.courseNotificationCount = count;
            });
        }
    }

    ngOnDestroy(): void {
        if (this.courseNotificationCountSubscription) {
            this.courseNotificationCountSubscription.unsubscribe();
        }
    }

    ngOnChanges() {
        if (this.course.exercises && this.course.exercises.length > 0) {
            this.exerciseCount = this.course.exercises.length;

            const nextExercisesWithAnyScore = this.exerciseService.getNextExercisesForDays(this.course.exercises!);
            // filters out every already successful (100%) exercise, only exercises left that still need work
            const nextExercises = nextExercisesWithAnyScore.filter((exercise: Exercise) => !exercise.studentParticipations?.[0]?.submissions?.[0]?.results?.[0]?.successful);

            if (nextExercises.length > 0 && nextExercises[0]) {
                this.nextRelevantExercise = nextExercises[0];
            }

            const totalScoresForCourse: CourseScores | undefined = this.scoresStorageService.getStoredTotalScores(this.course.id!);
            if (totalScoresForCourse) {
                this.totalRelativeScore = totalScoresForCourse.studentScores[ScoreType.CURRENT_RELATIVE_SCORE];
                this.totalAbsoluteScore = totalScoresForCourse.studentScores[ScoreType.ABSOLUTE_SCORE];
                this.totalReachableScore = totalScoresForCourse[ScoreType.REACHABLE_POINTS];
            }

            // Adjust for bonus points, i.e. when the student has achieved more than is reachable
            const scoreNotReached = roundValueSpecifiedByCourseSettings(Math.max(0, this.totalReachableScore - this.totalAbsoluteScore), this.course);
            this.ngxDoughnutData[0].value = this.totalAbsoluteScore;
            this.ngxDoughnutData[1].value = scoreNotReached;
            this.ngxDoughnutData = [...this.ngxDoughnutData];
        }
    }

    /**
     * Delegates the user to the corresponding course page when clicking on the chart
     */
    onSelect(): void {
        this.router.navigate(['courses', this.course.id]);
    }
}
