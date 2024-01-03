import { Component, Input, OnChanges } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { Course } from 'app/entities/course.model';
import { Exercise, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { CachingStrategy } from 'app/shared/image/secured-image.component';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { GraphColors } from 'app/entities/statistics.model';
import { ScoresStorageService } from 'app/course/course-scores/scores-storage.service';
import { ScoreType } from 'app/shared/constants/score-type.constants';
import { CourseScores } from 'app/course/course-scores/course-scores';

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
    nextExerciseDueDate?: dayjs.Dayjs;
    nextExerciseIcon: IconProp;
    nextExerciseTooltip: string;
    exerciseCount = 0;
    lectureCount = 0;
    examCount = 0;

    totalRelativeScore: number;
    totalReachableScore: number;
    totalAbsoluteScore: number;

    courseColor: string;

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

    constructor(
        private router: Router,
        private route: ActivatedRoute,
        private scoresStorageService: ScoresStorageService,
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
                this.updateNextDueDate();
                this.nextExerciseIcon = getIcon(this.nextRelevantExercise!.type);
                this.nextExerciseTooltip = getIconTooltip(this.nextRelevantExercise!.type);
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

        this.lectureCount = this.course.numberOfLectures ?? this.course.lectures?.length ?? 0;
        this.examCount = this.course.numberOfExams ?? this.course.exams?.length ?? 0;
        this.courseColor = this.course.color || this.ARTEMIS_DEFAULT_COLOR;
    }

    /**
     * Delegates the user to the corresponding course page when clicking on the chart
     */
    onSelect(): void {
        this.router.navigate(['courses', this.course.id]);
    }

    /**
     * Navigates to the exam page of the course.
     * We stop the propagation of the click event to avoid navigating to the exercise page.
     * @param event the click event
     */
    navigateToExams(event: Event) {
        event.stopPropagation();
        this.router.navigate(['courses', this.course.id, 'exams']);
    }

    private updateNextDueDate() {
        let nextExerciseDueDate = undefined;
        if (this.nextRelevantExercise) {
            if (this.nextRelevantExercise.studentParticipations && this.nextRelevantExercise.studentParticipations.length > 0) {
                nextExerciseDueDate = getExerciseDueDate(this.nextRelevantExercise, this.nextRelevantExercise.studentParticipations[0]);
            } else {
                nextExerciseDueDate = this.nextRelevantExercise.dueDate;
            }
        }
        this.nextExerciseDueDate = nextExerciseDueDate;
    }
}
