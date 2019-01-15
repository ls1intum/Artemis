import { Component, OnInit, Input } from '@angular/core';
import { Course, CourseScoreCalculationService } from 'app/entities/course';
import { Exercise, ExerciseService } from 'app/entities/exercise';

@Component({
    selector: 'jhi-overview-course-card',
    templateUrl: './overview-course-card.component.html',
    styleUrls: ['overview-course-card.scss']
})
export class OverviewCourseCardComponent implements OnInit {
    @Input() course: Course;

    constructor(
        private courseScoreCalculationService: CourseScoreCalculationService,
        private exerciseService: ExerciseService
    ) {
    }

    ngOnInit() {
    }

    // TODO: remove again!
    get randomColor(): string {
        const colors = ['danger', 'info', 'success', 'warning'];
        const randomIndex = (this.course.id % colors.length) - 1;
        return colors[randomIndex];
    }

    displayTotalRelativeScore(): number {
        if (this.course.exercises.length > 0) {
            return this.courseScoreCalculationService.calculateTotalScores(this.course.exercises).get('relativeScore');
        } else {
            return 0;
        }
    }

    nextRelevantExercises(): Exercise[] {
        return this.exerciseService.getNextExercisesForDays(this.course.exercises);
    }

    startExercise(exercise: Exercise): void {
        console.log(exercise);
        console.warn('NYI');
    }
}
