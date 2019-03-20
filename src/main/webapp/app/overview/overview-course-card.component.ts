import { Component, OnInit, Input } from '@angular/core';
import { Course, CourseScoreCalculationService } from 'app/entities/course';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { ActivatedRoute, Router } from '@angular/router';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';

@Component({
    selector: 'jhi-overview-course-card',
    templateUrl: './overview-course-card.component.html',
    styleUrls: ['overview-course-card.scss']
})
export class OverviewCourseCardComponent implements OnInit {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    @Input() course: Course;

    constructor(
        private router: Router,
        private route: ActivatedRoute,
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

    get nextRelevantExercise(): Exercise {
        return this.exerciseService.getNextExerciseForDays(this.course.exercises);
    }

    startExercise(exercise: Exercise): void {
        this.router.navigate([this.course.id, 'exercises', exercise.id], {relativeTo: this.route});
    }
}
