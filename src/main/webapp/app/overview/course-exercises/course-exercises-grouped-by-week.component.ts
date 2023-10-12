import { Component, Input } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseWithDueDate } from 'app/overview/course-exercises/course-exercises.component';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-course-exercises-grouped-by-week',
    templateUrl: './course-exercises-grouped-by-week.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesGroupedByWeekComponent {
    @Input() nextRelevantExercise?: ExerciseWithDueDate;
    @Input() isVisibleToStudents: (exercise: Exercise) => boolean | undefined;
    @Input() course: Course;
    @Input() exerciseForGuidedTour?: Exercise;
    @Input() weeklyIndexKeys: string[];
    @Input() weeklyExercisesGrouped: object;

    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    toggleCollapsed(weekKey: string) {
        if (this.weeklyExercisesGrouped[weekKey]) {
            this.weeklyExercisesGrouped[weekKey].isCollapsed = !this.weeklyExercisesGrouped[weekKey].isCollapsed;
        }
    }
}
