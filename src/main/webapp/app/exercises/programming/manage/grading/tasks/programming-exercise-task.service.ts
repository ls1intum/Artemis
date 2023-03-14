import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { Observable } from 'rxjs';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { ProgrammingExerciseGradingStatistics, TestCaseStats } from 'app/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';

@Injectable()
export class ProgrammingExerciseTaskService {
    exercise: ProgrammingExercise;
    course: Course;
    gradingStatistics: ProgrammingExerciseGradingStatistics;

    tasks: ProgrammingExerciseTask[];
    totalWeights: number;
    maxPoints: number;

    public resourceUrl = `${SERVER_API_URL}api/programming-exercises`;

    constructor(private http: HttpClient) {}

    public configure(exercise: ProgrammingExercise, course: Course, gradingStatistics: ProgrammingExerciseGradingStatistics) {
        this.exercise = exercise;
        this.course = course;
        this.gradingStatistics = gradingStatistics;

        this.maxPoints = this.exercise.maxPoints ?? 0;
        this.initializeTasks();
    }

    private initializeTasks = () => {
        this.getTasksByExercise(this.exercise).subscribe((serverSideTasks) => {
            const tasks = (serverSideTasks ?? []).map((task) => task as ProgrammingExerciseTask).map(this.updateTask);
            this.totalWeights = sum(tasks.map((task) => task.weight ?? 0));
            this.tasks = tasks.map(this.updateTaskPoints).map(this.addGradingStats);
        });
    };

    private addGradingStats = (task: ProgrammingExerciseTask): ProgrammingExerciseTask => {
        task.stats = new TestCaseStats();

        task.testCases.forEach((testCase: ProgrammingExerciseTestCase) => {
            const testStats = this.gradingStatistics.testCaseStatsMap![testCase.testName!];
            testCase.testCaseStats = testStats;

            task.stats!.numPassed += testStats.numPassed;
            task.stats!.numFailed += testStats.numFailed;
        });

        return task;
    };

    public getTasksByExercise = (exercise: Exercise): Observable<ProgrammingExerciseServerSideTask[]> => {
        return this.http.get<ProgrammingExerciseServerSideTask[]>(`${this.resourceUrl}/${exercise.id}/tasks`);
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
