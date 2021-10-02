import { Component, Input, OnChanges } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { Course } from 'app/entities/course.model';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { CourseStatisticsDataSet } from 'app/overview/course-statistics/course-statistics.component';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { roundScore } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-overview-course-card',
    templateUrl: './course-card.component.html',
    styleUrls: ['course-card.scss'],
})
export class CourseCardComponent implements OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    @Input() course: Course;
    @Input() hasGuidedTour: boolean;

    CachingStrategy = CachingStrategy;

    nextRelevantExercise?: Exercise;
    nextExerciseIcon: IconProp;
    nextExerciseTooltip: string;
    exerciseCount = 0;
    lectureCount = 0;
    examCount = 0;

    totalRelativeScore: number;
    totalReachableScore: number;
    totalAbsoluteScore: number;

    doughnutChartColors: any[] = ['limegreen', 'red'];
    doughnutChartLabels: string[] = ['Achieved Points', 'Missing Points'];
    doughnutChartData: CourseStatisticsDataSet[] = [
        {
            data: [0, 0],
            backgroundColor: this.doughnutChartColors,
        },
    ];
    totalScoreOptions: object = {
        animation: false,
        cutoutPercentage: 75,
        scaleShowVerticalLines: false,
        responsive: false,
        tooltips: {
            backgroundColor: 'rgba(0, 0, 0, 1)',
        },
    };

    constructor(
        private router: Router,
        private route: ActivatedRoute,
        private courseScoreCalculationService: CourseScoreCalculationService,
        private exerciseService: ExerciseService,
    ) {}

    ngOnChanges() {
        if (this.course.exercises && this.course.exercises.length > 0) {
            this.exerciseCount = this.course.exercises.length;

            const nextExercisesWithAnyScore = this.exerciseService.getNextExercisesForDays(this.course.exercises!);
            // filters out every already successful (100%) exercise, only exercises left that still need work
            const nextExercises = nextExercisesWithAnyScore.filter((exercise: Exercise) => !exercise.studentParticipations?.[0]?.submissions?.[0]?.results?.[0]?.successful);

            if (nextExercises.length > 0 && nextExercises[0]) {
                this.nextRelevantExercise = nextExercises[0];
                this.nextExerciseIcon = getIcon(this.nextRelevantExercise!.type);
                this.nextExerciseTooltip = getIconTooltip(this.nextRelevantExercise!.type);
            }

            const scores = this.courseScoreCalculationService.calculateTotalScores(this.course.exercises, this.course);
            this.totalRelativeScore = scores.get('currentRelativeScore')!;
            this.totalAbsoluteScore = scores.get('absoluteScore')!;
            this.totalReachableScore = scores.get('reachableScore')!;

            // Adjust for bonus points, i.e. when the student has achieved more than is reachable
            const scoreNotReached = roundScore(Math.max(0, this.totalReachableScore - this.totalAbsoluteScore), this.course);
            this.doughnutChartData[0].data = [this.totalAbsoluteScore, scoreNotReached];
        }

        if (this.course.lectures) {
            this.lectureCount = this.course.lectures.length;
        }

        if (this.course.exams) {
            this.examCount = this.course.exams.length;
        }
    }
}
