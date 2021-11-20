import { Component, Input, OnChanges } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { Course } from 'app/entities/course.model';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { roundScoreSpecifiedByCourseSettings } from 'app/shared/util/utils';

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

    // ngx
    ngxDoughnutData: any[] = [
        { name: 'achievedPointsLabel', value: 0 },
        { name: 'missingPointsLabel', value: 0 },
    ];
    ngxColor = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: ['#32cd32', '#ff0000'], // colors: green, red
    } as Color;

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
            const scoreNotReached = roundScoreSpecifiedByCourseSettings(Math.max(0, this.totalReachableScore - this.totalAbsoluteScore), this.course);
            this.ngxDoughnutData[0].value = this.totalAbsoluteScore;
            this.ngxDoughnutData[1].value = scoreNotReached;
            this.ngxDoughnutData = [...this.ngxDoughnutData];
        }

        if (this.course.lectures) {
            this.lectureCount = this.course.lectures.length;
        }

        if (this.course.exams) {
            this.examCount = this.course.exams.length;
        }
    }

    tooltipFormatting(value: any): string {
        return value.name;
    }

    stringify(value: any) {
        console.log(JSON.parse(JSON.stringify(value)));
    }

    onSelect(event: any) {
        this.router.navigate(['courses', this.course.id]);
    }
}
