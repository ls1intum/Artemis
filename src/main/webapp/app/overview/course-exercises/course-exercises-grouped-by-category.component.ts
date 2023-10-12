import { Component, Input, OnChanges } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm/';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';

type ExerciseGroup = 'current' | 'future' | 'previous' | 'noDueDate';

type ExerciseGroups = Record<ExerciseGroup, Exercise[]>;

@Component({
    selector: 'jhi-course-exercises-grouped-by-category',
    templateUrl: './course-exercises-grouped-by-category.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesGroupedByCategoryComponent implements OnChanges {
    @Input() filteredExercises?: Exercise[];
    @Input() course?: Course;
    @Input() exerciseForGuidedTour?: Exercise;

    exerciseGroups: ExerciseGroups;

    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    ngOnChanges() {
        this.exerciseGroups = this.groupExercisesByDueDate();
    }

    private groupExercisesByDueDate(): ExerciseGroups {
        const updatedExerciseGroups: ExerciseGroups = {
            current: [],
            future: [],
            previous: [],
            noDueDate: [],
        };

        if (!this.filteredExercises) {
            return updatedExerciseGroups;
        }

        for (const exercise of this.filteredExercises) {
            const exerciseGroup = this.getExerciseGroup(exercise);
            updatedExerciseGroups[exerciseGroup].push(exercise);
        }

        return updatedExerciseGroups;
    }

    private getExerciseGroup(exercise: Exercise): ExerciseGroup {
        if (!exercise.dueDate) {
            return 'noDueDate';
        }

        const dueDate = dayjs(exercise.dueDate);
        const now = dayjs();

        const dueDateIsInThePast = dueDate.isBefore(now);
        if (dueDateIsInThePast) {
            return 'previous';
        }

        const dueDateIsWithinNextWeek = dueDate.isBefore(now.add(1, 'week'));
        if (dueDateIsWithinNextWeek) {
            return 'current';
        }

        return 'future';
    }

    protected readonly Object = Object;
}
