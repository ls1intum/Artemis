import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiToggleSwitchComponent } from '@tumaet/ui-angular/toggle-switch';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { ProgrammingExerciseTaskService } from 'app/programming/manage/grading/tasks/programming-exercise-task.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { faAngleDown } from '@fortawesome/free-solid-svg-icons/faAngleDown';
import { faAngleRight } from '@fortawesome/free-solid-svg-icons/faAngleRight';
import { faAsterisk } from '@fortawesome/free-solid-svg-icons/faAsterisk';
import { faMedal } from '@fortawesome/free-solid-svg-icons/faMedal';
import { faQuestionCircle } from '@fortawesome/free-solid-svg-icons/faQuestionCircle';
import { faScaleUnbalanced } from '@fortawesome/free-solid-svg-icons/faScaleUnbalanced';
import { faSort } from '@fortawesome/free-solid-svg-icons/faSort';
import { faSortDown } from '@fortawesome/free-solid-svg-icons/faSortDown';
import { faSortUp } from '@fortawesome/free-solid-svg-icons/faSortUp';
import { ProgrammingExerciseGradingStatistics } from 'app/programming/shared/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTask } from '../programming-exercise-task';
import { Observable, Subject } from 'rxjs';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { isExamExercise } from 'app/foundation/util/utils';
import { ProgrammingExerciseServerSideTask } from 'app/programming/shared/entities/programming-exercise-task.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgrammingExerciseTaskComponent } from '../programming-exercise-task/programming-exercise-task.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

type Sort = {
    by: 'name' | 'weight' | 'multiplier' | 'bonusPoints' | 'visibility' | 'resulting' | 'type';
    descending: boolean;
};

type TaskComparator = (a: ProgrammingExerciseTask | ProgrammingExerciseTestCase, b: ProgrammingExerciseTask | ProgrammingExerciseTestCase) => number;

@Component({
    selector: 'jhi-programming-exercise-grading-tasks-table',
    templateUrl: './programming-exercise-grading-tasks-table.component.html',
    styleUrls: ['./programming-exercise-grading-tasks-table.css'],
    imports: [
        TumAetUiButtonDirective,
        TranslateDirective,
        FormsModule,
        TumAetUiTooltipDirective,
        TumAetUiToggleSwitchComponent,
        FaIconComponent,
        ProgrammingExerciseTaskComponent,
        ArtemisTranslatePipe,
    ],
})
export class ProgrammingExerciseGradingTasksTableComponent implements OnInit {
    private taskService = inject(ProgrammingExerciseTaskService);

    readonly exercise = input.required<ProgrammingExercise>();
    readonly course = input.required<Course>();
    readonly gradingStatisticsObservable = input.required<Observable<ProgrammingExerciseGradingStatistics>>();

    // Icons
    faAngleDown = faAngleDown;
    faAngleRight = faAngleRight;
    faQuestionCircle = faQuestionCircle;
    faScaleUnbalanced = faScaleUnbalanced;
    faMedal = faMedal;
    faAsterisk = faAsterisk;

    readonly isSaving = signal(false);
    readonly tasks = signal<ProgrammingExerciseTask[]>([]);
    readonly allTasksExpandedSubject = signal<Subject<boolean>>(undefined!);
    /**
     * Incremented whenever the tasks or their test cases are changed in place: when the points are recalculated and when the test cases are sorted. The task objects keep
     * their identity then, so the OnPush task rows would not notice; passing the revision to every row makes all of them render the new state.
     */
    readonly rowsRevision = signal(0);

    currentSort: Sort | undefined;

    readonly isExamExercise = signal(false);

    get ignoreInactive() {
        return this.taskService.ignoreInactive;
    }

    ngOnInit(): void {
        this.allTasksExpandedSubject.set(new Subject());
        this.gradingStatisticsObservable().subscribe((gradingStatistics) => {
            this.taskService.configure(this.exercise(), this.course(), gradingStatistics).subscribe(this.updateTasks);
        });

        this.currentSort = {
            by: 'name',
            descending: true,
        };

        this.isExamExercise.set(isExamExercise(this.exercise()));
    }

    updateTasks = () => {
        this.tasks.set(this.taskService.updateTasks());
        this.rowsRevision.update((revision) => revision + 1);
    };

    toggleShowInactiveTestsShown = () => {
        this.taskService.toggleIgnoreInactive();
        this.updateTasks();
    };

    saveTestCases = () => {
        this.isSaving.set(true);
        this.taskService.saveTestCases().subscribe(() => this.isSaving.set(false));
    };

    resetTestCases = () => {
        this.isSaving.set(true);
        this.taskService.resetTestCases().subscribe(() => {
            this.isSaving.set(false);
            this.updateTasks();
        });
    };

    toggleAllTasksExpanded = (value: boolean) => {
        this.allTasksExpandedSubject().next(value);
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

        let comparator = (a: ProgrammingExerciseTask | ProgrammingExerciseTestCase, b: ProgrammingExerciseTask | ProgrammingExerciseTestCase) => {
            const order = comparators[this.currentSort!['by']](a, b);
            return this.currentSort?.descending ? order : -order;
        };

        const sortedTasks = [...this.tasks()].sort(comparator);

        // the objects task and test have their name attribute named differently, making this necessary
        if (this.currentSort?.by === 'name') {
            comparator = (a: ProgrammingExerciseTask | ProgrammingExerciseTestCase, b: ProgrammingExerciseTask | ProgrammingExerciseTestCase) => {
                const order = this.compareStringForAttribute<ProgrammingExerciseTestCase>('testName')(a, b);
                return this.currentSort?.descending ? order : -order;
            };
        }
        sortedTasks.filter(({ testCases }) => testCases).forEach((task) => task.testCases.sort(comparator));
        this.tasks.set(sortedTasks);
        // the test cases of each task were sorted in place, which no row sees without it
        this.rowsRevision.update((revision) => revision + 1);
    };

    private compareNumForAttribute = <T extends ProgrammingExerciseTask | ProgrammingExerciseTestCase>(attributeKey: keyof T): TaskComparator => {
        return (a: ProgrammingExerciseTask | ProgrammingExerciseTestCase, b: ProgrammingExerciseTask | ProgrammingExerciseTestCase) => {
            return (((a as T)[attributeKey] as number) ?? 0) - (((b as T)[attributeKey] as number) ?? 0);
        };
    };

    /**
     * {@link ProgrammingExerciseTask} extends {@link ProgrammingExerciseServerSideTask} which is why we need to explicitly add it here for the type
     * @param attributeKey
     */
    private compareStringForAttribute = <T extends ProgrammingExerciseTask | ProgrammingExerciseServerSideTask | ProgrammingExerciseTestCase>(
        attributeKey: keyof T,
    ): TaskComparator => {
        return (a: ProgrammingExerciseTask | ProgrammingExerciseTestCase, b: ProgrammingExerciseTask | ProgrammingExerciseTestCase) => {
            const aType = (a as T)[attributeKey] ?? '';
            const bType = (b as T)[attributeKey] ?? '';

            if (aType < bType) return -1;
            if (aType > bType) return 1;
            return 0;
        };
    };
}
