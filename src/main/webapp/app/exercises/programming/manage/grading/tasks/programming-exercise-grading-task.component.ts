import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleRight, faAsterisk, faMedal, faQuestionCircle, faScaleUnbalanced, faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTask } from './programming-exercise-task';
import { Observable, Subject } from 'rxjs';

type Sort = {
    by: 'name' | 'weight' | 'multiplier' | 'bonusPoints' | 'visibility' | 'resulting' | 'type';
    descending: boolean;
};

type TaskComparator = (a: ProgrammingExerciseTask, b: ProgrammingExerciseTask) => number;

@Component({
    selector: 'jhi-programming-exercise-grading-task',
    templateUrl: './programming-exercise-grading-task.component.html',
    styleUrls: ['./programming-exercise-grading-task.scss'],
})
export class ProgrammingExerciseGradingTaskComponent implements OnInit {
    @Input() exercise: ProgrammingExercise;
    @Input() course: Course;
    @Input() gradingStatisticsObservable: Observable<ProgrammingExerciseGradingStatistics>;

    // Icons
    faAngleDown = faAngleDown;
    faAngleRight = faAngleRight;
    faQuestionCircle = faQuestionCircle;
    faScaleUnbalanced = faScaleUnbalanced;
    faMedal = faMedal;
    faAsterisk = faAsterisk;

    isSaving = false;
    tasks: ProgrammingExerciseTask[];
    allTasksExpandedSubject: Subject<boolean>;

    currentSort: Sort | undefined;

    get ignoreInactive() {
        return this.taskService.ignoreInactive;
    }

    constructor(private taskService: ProgrammingExerciseTaskService) {}

    ngOnInit(): void {
        this.allTasksExpandedSubject = new Subject();
        this.gradingStatisticsObservable.subscribe((gradingStatistics) => {
            this.taskService.configure(this.exercise, this.course, gradingStatistics).subscribe(this.updateTasks);
        });

        this.currentSort = {
            by: 'name',
            descending: true,
        };
    }

    updateTasks = () => {
        this.tasks = this.taskService.updateTasks();
    };

    toggleShowInactiveTestsShown = () => {
        this.taskService.toggleIgnoreInactive();
        this.updateTasks();
    };

    saveTestCases = () => {
        this.isSaving = true;
        this.taskService.saveTestCases().subscribe(() => (this.isSaving = false));
    };

    resetTestCases = () => {
        this.isSaving = true;
        this.taskService.resetTestCases().subscribe(() => {
            this.isSaving = false;
            this.updateTasks();
        });
    };

    toggleAllTasksExpanded = (value: boolean) => {
        this.allTasksExpandedSubject.next(value);
    };

    changeSort = (by: Sort['by']) => {
        if (this.currentSort?.by === by) {
            this.currentSort.descending = !this.currentSort.descending;
        } else {
            this.currentSort = {
                by: by,
                descending: true,
            };
        }

        this.sort();
    };

    getSortIcon = (by: Sort['by']) => {
        if (this.currentSort?.by !== by) {
            return faSort;
        }

        return this.currentSort.descending ? faSortDown : faSortUp;
    };

    private sort = () => {
        const comparators = {
            name: this.compareStringForAttribute('taskName'),
            weight: this.compareNumForAttribute('weight'),
            multiplier: this.compareNumForAttribute('bonusMultiplier'),
            bonusPoints: this.compareNumForAttribute('bonusPoints'),
            visibility: this.compareStringForAttribute('visibility'),
            resulting: this.compareNumForAttribute('resultingPoints'),
            type: this.compareStringForAttribute('type'),
        };
        const comparator = (a: ProgrammingExerciseTask, b: ProgrammingExerciseTask) => {
            const order = comparators[this.currentSort!['by']](a, b);
            return this.currentSort?.descending ? order : -order;
        };

        this.tasks = this.tasks.sort(comparator);
    };

    private compareNumForAttribute = (attributeKey: keyof ProgrammingExerciseTask): TaskComparator => {
        return (a, b) => {
            return ((a[attributeKey] as number) ?? 0) - ((b[attributeKey] as number) ?? 0);
        };
    };

    private compareStringForAttribute = (attributeKey: keyof ProgrammingExerciseTask): TaskComparator => {
        return (a, b) => {
            const aType = a[attributeKey] ?? '';
            const bType = b[attributeKey] ?? '';

            if (aType < bType) return -1;
            if (aType > bType) return 1;
            return 0;
        };
    };
}
