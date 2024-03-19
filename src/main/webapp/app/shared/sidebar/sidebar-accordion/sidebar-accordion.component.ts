import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm/';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { cloneDeep } from 'lodash-es';

type ExerciseGroupCategory = 'past' | 'current' | 'future' | 'noDueDate';

/**
 * {@link ExerciseGroupCategory#past} is always collapsed by default
 */

type ExerciseGroups = Record<ExerciseGroupCategory, { exercises: Exercise[] }>;
type ExerciseCollapseState = Record<ExerciseGroupCategory, { isCollapsed: boolean }>;

const DEFAULT_EXERCISE_GROUPS: ExerciseGroups = {
    future: { exercises: [] },
    current: { exercises: [] },
    past: { exercises: [] },
    noDueDate: { exercises: [] },
};
const DEFAULT_EXERCISE_COLLAPSE_STATE: ExerciseCollapseState = {
    future: { isCollapsed: true },
    current: { isCollapsed: false },
    past: { isCollapsed: true },
    noDueDate: { isCollapsed: true },
};

@Component({
    selector: 'jhi-sidebar-accordion',
    templateUrl: './sidebar-accordion.component.html',
    styleUrls: ['./sidebar-accordion.component.scss'],
})
export class SidebarAccordionComponent implements OnChanges, OnInit {
    protected readonly Object = Object;

    @Input() entityData?: Exercise[];
    @Input() course?: Course;
    @Input() exerciseForGuidedTour?: Exercise;
    @Input() searchValue: string;
    @Input() routeParams: any;

    storedCollapseState: string | null;
    exerciseGroups: ExerciseGroups = cloneDeep(DEFAULT_EXERCISE_GROUPS);
    collapseState = DEFAULT_EXERCISE_COLLAPSE_STATE;

    //icon
    faChevronRight = faChevronRight;

    ngOnInit() {
        this.exerciseGroups = this.groupExercisesByDueDate();
        this.expandGroupWithSelectedExercise();
        this.setStoredCollapseState();
    }

    ngOnChanges() {
        if (this.searchValue) {
            this.expandAll();
        } else {
            this.exerciseGroups = this.groupExercisesByDueDate();
            this.setStoredCollapseState();
        }
    }

    setStoredCollapseState() {
        this.storedCollapseState = sessionStorage.getItem('sidebar.collapseState');
        if (this.storedCollapseState) this.collapseState = JSON.parse(this.storedCollapseState);
    }

    expandAll() {
        Object.values(this.collapseState).forEach((collapseState) => {
            collapseState.isCollapsed = false;
        });
    }

    expandGroupWithSelectedExercise() {
        if (this.routeParams.exerciseId) {
            const groupWithSelectedExercise = Object.entries(this.exerciseGroups).find((exerciseGroup) =>
                exerciseGroup[1].exercises.some((exercise: Exercise) => exercise.id === Number(this.routeParams.exerciseId)),
            );
            if (groupWithSelectedExercise) {
                const groupName = groupWithSelectedExercise[0];
                this.collapseState[groupName].isCollapsed = false;
            }
        }
    }

    toggleGroupCategoryCollapse(exerciseGroupCategoryKey: string) {
        this.collapseState[exerciseGroupCategoryKey].isCollapsed = !this.collapseState[exerciseGroupCategoryKey].isCollapsed;
        sessionStorage.setItem('sidebar.collapseState', JSON.stringify(this.collapseState));
    }

    private groupExercisesByDueDate(): ExerciseGroups {
        const groupedExerciseGroups: ExerciseGroups = cloneDeep(DEFAULT_EXERCISE_GROUPS);
        if (!this.entityData) {
            return groupedExerciseGroups;
        }

        for (const exercise of this.entityData) {
            const exerciseGroup = this.getCorrespondingExerciseGroup(exercise);
            groupedExerciseGroups[exerciseGroup].exercises.push(exercise);
        }

        return groupedExerciseGroups;
    }

    private getCorrespondingExerciseGroup(exercise: Exercise): ExerciseGroupCategory {
        if (!exercise.dueDate) {
            return 'noDueDate';
        }

        const dueDate = dayjs(exercise.dueDate);
        const now = dayjs();

        const dueDateIsInThePast = dueDate.isBefore(now);
        if (dueDateIsInThePast) {
            return 'past';
        }

        const dueDateIsWithinNextWeek = dueDate.isBefore(now.add(1, 'week'));
        if (dueDateIsWithinNextWeek) {
            return 'current';
        }

        return 'future';
    }

    /**
     * This is a workaround to make {@link DEFAULT_EXERCISE_GROUPS} accessible for tests without exporting it
     */
    getDefaultExerciseGroups() {
        return DEFAULT_EXERCISE_GROUPS;
    }
}
