import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { Observable, catchError, of, tap } from 'rxjs';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { ProgrammingExerciseGradingStatistics, TestCaseStats } from 'app/entities/programming/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming/programming-exercise-test-case.model';
import { ProgrammingExerciseGradingService, ProgrammingExerciseTestCaseUpdate } from '../../services/programming-exercise-grading.service';
import { AlertService } from 'app/core/util/alert.service';
import { map, mergeMap } from 'rxjs/operators';

@Injectable()
export class ProgrammingExerciseTaskService {
    private http = inject(HttpClient);
    private alertService = inject(AlertService);
    private gradingService = inject(ProgrammingExerciseGradingService);

    exercise: ProgrammingExercise;
    course: Course;
    gradingStatistics: ProgrammingExerciseGradingStatistics;

    maxPoints: number;

    currentTasks: ProgrammingExerciseTask[];
    tasks: ProgrammingExerciseTask[];

    ignoreInactive = true;

    public resourceUrl = 'api/programming-exercises';

    get totalWeights() {
        return sum(this.testCases.map(({ weight }) => weight ?? 0));
    }

    get testCases(): ProgrammingExerciseTestCase[] {
        return this.currentTasks.flatMap((task) => task.testCases);
    }

    public hasUnsavedChanges(): boolean {
        // service has not been configured yet
        if (!this.tasks || !this.currentTasks) {
            return false;
        }

        return this.testCases.some(({ changed }) => changed);
    }

    /**
     * Updates all resulting task points
     */
    public updateTasks(): ProgrammingExerciseTask[] {
        this.updateAllTaskPoints();
        return this.currentTasks;
    }

    /**
     * Toggles whether inactive test cases are shown and handled
     */
    public toggleIgnoreInactive() {
        this.ignoreInactive = !this.ignoreInactive;

        this.setCurrentTasks();
        this.updateAllTaskPoints();
    }

    /**
     * Configures the task service with the required models and returns the initial list of tasks
     * @param exercise for point calculation
     * @param course for correct rounding
     * @param gradingStatistics corresponding to the test cases
     */
    public configure(exercise: ProgrammingExercise, course: Course, gradingStatistics: ProgrammingExerciseGradingStatistics): Observable<ProgrammingExerciseTask[]> {
        this.exercise = exercise;
        this.course = course;
        this.gradingStatistics = gradingStatistics;

        this.maxPoints = this.exercise.maxPoints ?? 0;

        return this.getTasksByExercise(this.exercise).pipe(map(this.initializeTasks));
    }

    /**
     * Save the test case configuration contained in each task
     */
    public saveTestCases(): Observable<ProgrammingExerciseTestCase[] | undefined | null> {
        const testCasesToUpdate = this.currentTasks
            .map((task) => task.testCases)
            .flatMap((testcase) => testcase)
            .filter((test) => test.changed);

        const testCaseUpdates = testCasesToUpdate.map((testCase) => ProgrammingExerciseTestCaseUpdate.from(testCase));
        const testCaseUpdatesWeightSum = sum(testCasesToUpdate.map((test) => test.weight));

        if (testCaseUpdatesWeightSum < 0) {
            this.alertService.error(`artemisApp.programmingExercise.configureGrading.testCases.weightSumError`);
            return of(undefined);
        }

        return this.gradingService.updateTestCase(this.exercise.id!, testCaseUpdates).pipe(
            tap((updatedTestCases: ProgrammingExerciseTestCase[]) => {
                // Update changed flag for test cases
                const updatedTestCaseIDs = updatedTestCases.map((updatedTest) => updatedTest.id);
                this.testCases
                    .filter((test) => updatedTestCaseIDs.includes(test.id))
                    .forEach((test) => {
                        test.changed = false;
                    });
                this.gradingService.notifyTestCases(this.exercise.id!, this.testCases);

                // Find out if there are test cases that were not updated, show an error.
                const notUpdatedTestCases = this.testCases.filter((test) => test.changed);
                if (notUpdatedTestCases.length) {
                    this.alertService.error(`artemisApp.programmingExercise.configureGrading.testCases.couldNotBeUpdated`, { testCases: notUpdatedTestCases });
                } else {
                    this.alertService.success(`artemisApp.programmingExercise.configureGrading.testCases.updated`);
                }
            }),
            catchError((error: HttpErrorResponse) => {
                if (error.status === 400 && error.error?.errorKey) {
                    this.alertService.error(`artemisApp.programmingExercise.configureGrading.testCases.` + error.error.errorKey, error.error);
                } else {
                    this.alertService.error(`artemisApp.programmingExercise.configureGrading.testCases.couldNotBeUpdated`, { testCases: testCasesToUpdate });
                }
                return of(null);
            }),
        );
    }

    /**
     * Reset all test cases.
     */
    public resetTestCases(): Observable<ProgrammingExerciseTestCase | ProgrammingExerciseTask[]> {
        return this.gradingService.resetTestCases(this.exercise.id!).pipe(
            tap((testCases: ProgrammingExerciseTestCase[]) => {
                this.alertService.success(`artemisApp.programmingExercise.configureGrading.testCases.resetSuccessful`);
                this.gradingService.notifyTestCases(this.exercise.id!, testCases);
            }),
            catchError(() => {
                this.alertService.error(`artemisApp.programmingExercise.configureGrading.testCases.resetFailed`);
                return of(undefined);
            }),
            mergeMap(() => {
                return this.getTasksByExercise(this.exercise).pipe(map(this.initializeTasks));
            }),
        );
    }

    /**
     * Remove duplicate test cases from the tasks array if instructor has added the same test case to multiple tasks
     * Test cases contained in multiple tasks get attributed to the first task they are found in
     */
    private removeDuplicateTestCasesFromTasks() {
        const testCaseSet = new Set<number>(); // Assuming 'id' is a unique identifier for ProgrammingExerciseTestCase

        for (const task of this.tasks) {
            const uniqueTestCases: ProgrammingExerciseTestCase[] = [];
            for (const testCase of task.testCases) {
                if (!testCaseSet.has(testCase.id!)) {
                    testCaseSet.add(testCase.id!);
                    uniqueTestCases.push(testCase);
                }
            }
            task.testCases = uniqueTestCases;
        }

        // Remove empty test cases
        this.tasks = this.tasks.filter((tasks) => tasks.testCases.length > 0);
    }

    private initializeTasks = (serverSideTasks: ProgrammingExerciseServerSideTask[]): ProgrammingExerciseTask[] => {
        this.tasks = serverSideTasks.map((task) => task as ProgrammingExerciseTask);

        this.tasks = this.tasks // configureTestCases needs tasks to be set be to be able to use the testCases getter
            .map((task) => ({ ...task, testCases: task.testCases ?? [] }))
            .map(this.addGradingStats);

        this.removeDuplicateTestCasesFromTasks();
        this.setCurrentTasks();
        this.updateAllTaskPoints();

        return this.currentTasks;
    };

    /*
     * Set the tasks currently displayed. Used for showing of active/inactive test cases
     */
    private setCurrentTasks = () => {
        const tasksCopy: ProgrammingExerciseTask[] = JSON.parse(JSON.stringify(this.tasks));
        if (this.ignoreInactive) {
            this.currentTasks = tasksCopy.filter((task) => {
                task.testCases = task.testCases.filter((test) => test.active);
                return task.testCases.length;
            });
        } else {
            this.currentTasks = tasksCopy;
        }

        // Initialize tasks after filtering/showing of inactive test cases
        this.currentTasks.forEach(this.initializeTask);
    };

    private getTasksByExercise = (exercise: Exercise): Observable<ProgrammingExerciseServerSideTask[]> => {
        return this.http.get<ProgrammingExerciseServerSideTask[]>(`${this.resourceUrl}/${exercise.id}/tasks-with-unassigned-test-cases`);
    };

    private addGradingStats = (task: ProgrammingExerciseTask): ProgrammingExerciseTask => {
        task.stats = new TestCaseStats();
        const testCaseStatsMap = this.gradingStatistics?.testCaseStatsMap;
        if (!testCaseStatsMap) {
            return task;
        }

        for (const testCase of task.testCases) {
            const testStats = testCaseStatsMap[testCase.testName!];
            if (!testStats) {
                continue;
            }

            testCase.testCaseStats = testStats;
            task.stats!.numPassed += testStats.numPassed;
            task.stats!.numFailed += testStats.numFailed;
        }

        return task;
    };

    /**
     * Initialized task values according to the values of its test cases
     * @param task to be initialized
     */
    public initializeTask = (task: ProgrammingExerciseTask): ProgrammingExerciseTask => {
        task.weight = sum(task.testCases.map((testCase) => testCase.weight ?? 0));
        task.bonusMultiplier = getSingleValue(task.testCases.map((testCase) => testCase.bonusMultiplier));
        task.bonusPoints = sum(task.testCases.map((testCase) => testCase.bonusPoints ?? 0));
        task.visibility = getSingleValue(task.testCases.map((testCase) => testCase.visibility));
        task.type = getSingleValue(task.testCases.map((testCase) => testCase.type)) ?? 'MIXED';

        return task;
    };

    private updateAllTaskPoints = () => {
        this.currentTasks.forEach(this.updateTaskPoints);
    };

    private updateTaskPoints = (task: ProgrammingExerciseTask) => {
        const [resultingPoints, resultingPointsPercent] = this.calculatePoints(task);

        task.resultingPoints = resultingPoints;
        task.resultingPointsPercent = resultingPointsPercent;
        task.testCases.forEach((test) => {
            const [resultingPoints, resultingPointsPercent] = this.calculatePoints(test);
            test.resultingPoints = resultingPoints;
            test.resultingPointsPercent = resultingPointsPercent;
        });
        return task;
    };

    private calculatePoints = (item: Pick<ProgrammingExerciseTask, 'weight' | 'bonusMultiplier' | 'bonusPoints'>): [number, number] => {
        const weight = Number(item.weight ?? 0);
        const multiplier = Number(item.bonusMultiplier ?? 1);
        const bonusPoints = Number(item.bonusPoints ?? 0);

        const points = ((weight * multiplier) / this.totalWeights) * this.maxPoints + bonusPoints;
        const resultingPoints = roundValueSpecifiedByCourseSettings(points, this.course);

        const relativePoints = (points / this.maxPoints) * 100;
        const resultingPointsPercent = roundValueSpecifiedByCourseSettings(relativePoints, this.course);

        return [resultingPoints, resultingPointsPercent];
    };
}

/**
 * Gets a single value from a list if there is only one unique value. otherwise returns undefined
 * @param values
 */
const getSingleValue = (values: any[]) => {
    const set = new Set(values);
    if (set.size == 1) {
        return set.values().next().value;
    }
};

const sum = (values: any[]): number => {
    return (values ?? []).reduce((a: number, b: number) => Number(a) + Number(b), 0);
};
