import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { Observable } from 'rxjs';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';

@Injectable()
export class ProgrammingExerciseTaskService {
    exercise: ProgrammingExercise;
    course: Course;
    tasks: ProgrammingExerciseTask[];

    totalWeights: number;
    maxPoints: number;

    public resourceUrl = `${SERVER_API_URL}api/programming-exercises`;

    constructor(private http: HttpClient) {}

    public configure(exercise: ProgrammingExercise, course: Course) {
        this.exercise = exercise;
        this.course = course;

        this.maxPoints = this.exercise.maxPoints ?? 0;

        this.getTasksByExercise(this.exercise).subscribe((serverSideTasks) => {
            const tasks = (serverSideTasks ?? []).map((task) => task as ProgrammingExerciseTask);
            this.totalWeights = sum(tasks.map((task) => task.weight));
            this.tasks = tasks.map((task) => this.updateTask(task));
        });
    }

    public getTasksByExercise(exercise: Exercise): Observable<ProgrammingExerciseServerSideTask[]> {
        return this.http.get<ProgrammingExerciseServerSideTask[]>(`${this.resourceUrl}/${exercise.id}/tasks`);
    }

    public updateTask(task: ProgrammingExerciseTask): ProgrammingExerciseTask {
        const newWeight = sum(task.testCases.map((testCase) => testCase.weight ?? 0));
        this.totalWeights += (task.weight ?? 0) - newWeight;
        task.weight = newWeight;

        task.bonusMultiplier = getSingleValue(task.testCases.map((testCase) => testCase.bonusMultiplier));
        task.bonusPoints = sum(task.testCases.map((testCase) => testCase.bonusPoints ?? 0));
        task.visibility = getSingleValue(task.testCases.map((testCase) => testCase.visibility));
        task.type = getSingleValue(task.testCases.map((testCase) => testCase.type));
        task.resultingPoints = (task.weight! / this.totalWeights) * this.maxPoints;

        return this.updateTaskPoints(task);
    }

    private updateTaskPoints(task: ProgrammingExerciseTask): ProgrammingExerciseTask {
        const weight = task.weight ?? 0;
        const multiplier = task.bonusMultiplier ?? 1;
        const bonusPoints = task.bonusPoints ?? 0;

        const points = ((weight * multiplier) / this.totalWeights) * this.maxPoints + bonusPoints;
        task.resultingPoints = roundValueSpecifiedByCourseSettings(points, this.course);
        const relativePoints = (points / this.maxPoints) * 100;
        task.resultingPointsPercent = roundValueSpecifiedByCourseSettings(relativePoints, this.course);
        return task;
    }
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
