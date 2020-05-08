import { Component, Input, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute, Router } from '@angular/router';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Component({
    selector: 'jhi-overview-course-card',
    templateUrl: './course-card.component.html',
    styleUrls: ['course-card.scss'],
})
export class CourseCardComponent implements OnInit {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;
    @Input() course: Course;
    @Input() hasGuidedTour: boolean;

    constructor(
        private router: Router,
        private route: ActivatedRoute,
        private courseScoreCalculationService: CourseScoreCalculationService,
        private exerciseService: ExerciseService,
    ) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {}

    /**
     * Gets the relative total score for all exercises in a course and returns it
     */
    displayTotalRelativeScore(): number {
        if (this.course.exercises.length > 0) {
            return this.courseScoreCalculationService.calculateTotalScores(this.course.exercises).get('relativeScore')!;
        } else {
            return 0;
        }
    }

    /**
     * Gets next relevant exercise from exercise service and returns it
     */
    get nextRelevantExercise(): Exercise {
        return this.exerciseService.getNextExerciseForDays(this.course.exercises);
    }

    /**
     * navigates through the exercise page for the given exercise and starts it
     * @param exercise - exercise to be started
     */
    startExercise(exercise: Exercise): void {
        this.router.navigate([this.course.id, 'exercises', exercise.id], { relativeTo: this.route });
    }
}
