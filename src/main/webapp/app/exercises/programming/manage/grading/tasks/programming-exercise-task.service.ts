import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { Observable, catchError, of, tap } from 'rxjs';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { ProgrammingExerciseGradingStatistics, TestCaseStats } from 'app/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExerciseGradingService, ProgrammingExerciseTestCaseUpdate } from '../../services/programming-exercise-grading.service';
import { AlertService } from 'app/core/util/alert.service';

@Injectable()
export class ProgrammingExerciseTaskService {
    exercise: ProgrammingExercise;
    course: Course;
    gradingStatistics: ProgrammingExerciseGradingStatistics;

    tasks: ProgrammingExerciseTask[];
    totalWeights: number;
    maxPoints: number;

    public resourceUrl = `${SERVER_API_URL}api/programming-exercises`;

    constructor(private http: HttpClient, private alertService: AlertService, private gradingService: ProgrammingExerciseGradingService) {}

    get testCases(): ProgrammingExerciseTestCase[] {
        return this.tasks.flatMap((task) => task.testCases);
    }

    public configure(exercise: ProgrammingExercise, course: Course, gradingStatistics: ProgrammingExerciseGradingStatistics) {
        this.exercise = exercise;
        this.course = course;
        this.gradingStatistics = gradingStatistics;

        this.maxPoints = this.exercise.maxPoints ?? 0;
        this.initializeTasks();
    }

    /**
     * Save the test case configuration contained in each task
     */
    public saveTestCases() {
        const testCasesToUpdate = this.tasks
            .map((task) => task.testCases)
            .flatMap((testcase) => testcase)
            .filter((test) => test.changed);

        const testCaseUpdates = testCasesToUpdate.map((testCase) => ProgrammingExerciseTestCaseUpdate.from(testCase));
        const testCaseUpdatesWeightSum = sum(testCasesToUpdate.map((test) => test.weight));

        if (testCaseUpdatesWeightSum < 0) {
            this.alertService.error(`artemisApp.programmingExercise.configureGrading.testCases.weightSumError`);
            return;
        }

        this.gradingService.updateTestCase(this.exercise.id!, testCaseUpdates).pipe(
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

    private initializeTasks = () => {
        this.getTasksByExercise(this.exercise).subscribe((serverSideTasks) => {
            const tasks = (serverSideTasks ?? []).map((task) => task as ProgrammingExerciseTask).map(this.updateTask);
            this.totalWeights = sum(tasks.map((task) => task.weight ?? 0));

            // Task points need to be updated again here since weight is not available before
            this.tasks = tasks.map(this.updateTaskPoints).map(this.addGradingStats);
        });
    };

    private getTasksByExercise = (exercise: Exercise): Observable<ProgrammingExerciseServerSideTask[]> => {
        return this.http.get<ProgrammingExerciseServerSideTask[]>(`${this.resourceUrl}/${exercise.id}/tasks`);
    };

    private addGradingStats = (task: ProgrammingExerciseTask): ProgrammingExerciseTask => {
        task.stats = new TestCaseStats();
        const testCaseStatsMap = this.gradingStatistics?.testCaseStatsMap;

        if (!testCaseStatsMap) {
            return task;
        }

        task.testCases.forEach((testCase: ProgrammingExerciseTestCase) => {
            const testStats = testCaseStatsMap[testCase.testName!];
            testCase.testCaseStats = testStats;

            task.stats!.numPassed += testStats.numPassed;
            task.stats!.numFailed += testStats.numFailed;
        });

        return task;
    };

    public updateTask = (task: ProgrammingExerciseTask): ProgrammingExerciseTask => {
        task.weight = sum(task.testCases.map((testCase) => testCase.weight ?? 0));
        task.bonusMultiplier = getSingleValue(task.testCases.map((testCase) => testCase.bonusMultiplier));
        task.bonusPoints = sum(task.testCases.map((testCase) => testCase.bonusPoints ?? 0));
        task.visibility = getSingleValue(task.testCases.map((testCase) => testCase.visibility));
        task.type = getSingleValue(task.testCases.map((testCase) => testCase.type));

        return this.updateTaskPoints(task);
    };

    public updateTaskPoints = (task: ProgrammingExerciseTask): ProgrammingExerciseTask => {
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
        const weight = item.weight ?? 0;
        const multiplier = item.bonusMultiplier ?? 1;
        const bonusPoints = item.bonusPoints ?? 0;

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
 * @private
 */
const getSingleValue = (values: any[]) => {
    const set = new Set(values);
    if (set.size == 1) {
        return set.values().next().value;
    }
};

const sum = (values: any[]) => {
    return (values ?? []).reduce((a: number, b: number) => Number(a) + Number(b));
};
