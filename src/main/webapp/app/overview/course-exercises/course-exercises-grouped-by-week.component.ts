import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseFilter, ExerciseWithDueDate } from 'app/overview/course-exercises/course-exercises.component';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Component({
    selector: 'jhi-course-exercises-grouped-by-week',
    templateUrl: './course-exercises-grouped-by-week.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesGroupedByWeekComponent implements OnInit, OnChanges {
    @Input() nextRelevantExercise?: ExerciseWithDueDate;
    @Input() course: Course;
    @Input() exerciseForGuidedTour?: Exercise;
    @Input() weeklyIndexKeys: string[];
    @Input() immutableWeeklyExercisesGrouped: object;
    @Input() activeFilters: Set<ExerciseFilter>;

    weeklyExercisesGrouped: object;

    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    ngOnInit() {
        this.weeklyExercisesGrouped = this.getAsMutableObject(this.immutableWeeklyExercisesGrouped);
    }

    ngOnChanges() {
        this.weeklyExercisesGrouped = this.getAsMutableObject(this.immutableWeeklyExercisesGrouped);
    }

    /**
     * Checks whether an exercise is visible to students or not
     * @param exercise The exercise which should be checked
     */
    isVisibleToStudents(exercise: Exercise): boolean | undefined {
        return !this.activeFilters.has(ExerciseFilter.UNRELEASED) || (exercise as QuizExercise)?.visibleToStudents;
    }

    private getAsMutableObject(object: any) {
        return { ...object };
    }
}
