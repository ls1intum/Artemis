import { Component, Input, OnChanges } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm/';
import { faAngleDown, faAngleUp, faChevronRight } from '@fortawesome/free-solid-svg-icons';

type ExerciseGroupCategory = 'current' | 'future' | 'previous' | 'noDueDate';

type ExerciseGroups = Record<ExerciseGroupCategory, { exercises: Exercise[]; isCollapsed: boolean }>;

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
    faChevronRight = faChevronRight;

    ngOnChanges() {
        this.exerciseGroups = this.groupExercisesByDueDate();
    }

    toggleGroupCategoryCollapse(exerciseGroupCategoryKey: string) {
        this.exerciseGroups[exerciseGroupCategoryKey].isCollapsed = !this.exerciseGroups[exerciseGroupCategoryKey].isCollapsed;
    }

    private groupExercisesByDueDate(): ExerciseGroups {
        const updatedExerciseGroups: ExerciseGroups = {
            previous: { exercises: [], isCollapsed: true },
            current: { exercises: [], isCollapsed: false },
            future: { exercises: [], isCollapsed: true },
            noDueDate: { exercises: [], isCollapsed: true },
        };

        if (!this.filteredExercises) {
            return updatedExerciseGroups;
        }

        for (const exercise of this.filteredExercises) {
            const exerciseGroup = this.getExerciseGroup(exercise);
            updatedExerciseGroups[exerciseGroup].exercises.push(exercise);
        }

        this.adjustExpandedOrCollapsedStateOfExerciseGroups(updatedExerciseGroups);

        return updatedExerciseGroups;
    }

    /**
     * 1. Keep the expanded or collapsed state of the exercise groups when a filter is applied
     * 2. Expand all sections with matches on search
     * 3. Make sure at least one displayed section is expanded by default
     */
    private adjustExpandedOrCollapsedStateOfExerciseGroups(exerciseGroups: ExerciseGroups) {
        const filterIsApplied = this.exerciseGroups;
        if (filterIsApplied) {
            Object.entries(exerciseGroups).forEach(([exerciseGroupKey, exerciseGroup]) => {
                exerciseGroup.isCollapsed = this.exerciseGroups[exerciseGroupKey].isCollapsed;
            });
        }

        const exerciseGroupsWithExercises = Object.entries(exerciseGroups).filter(([, exerciseGroup]) => exerciseGroup.exercises.length > 0);
        const expandedExerciseGroups = exerciseGroupsWithExercises.filter(([, exerciseGroup]) => !exerciseGroup.isCollapsed);

        const atLeastOneExerciseIsExpanded = expandedExerciseGroups.length > 0;
        if (!atLeastOneExerciseIsExpanded && exerciseGroupsWithExercises.length > 0) {
            exerciseGroupsWithExercises[0][1].isCollapsed = false;
        }
    }

    private getExerciseGroup(exercise: Exercise): ExerciseGroupCategory {
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
