import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { Observable } from 'rxjs';
import { Exercise } from 'app/entities/exercise.model';
import { ProgrammingExerciseTask, TaskAdditionalEnum } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseTaskService {
    public resourceUrl = `${SERVER_API_URL}api/programming-exercises`;

    constructor(private http: HttpClient) {}

    public getTasksByExercise(exercise: Exercise): Observable<ProgrammingExerciseServerSideTask[]> {
        return this.http.get<ProgrammingExerciseServerSideTask[]>(`${this.resourceUrl}/${exercise.id}/tasks`);
    }

    public updateValues(serverSideTask: ProgrammingExerciseServerSideTask): ProgrammingExerciseTask {
        const task = serverSideTask as ProgrammingExerciseTask;
        task.testCases = serverSideTask.testCases ?? [];
        task.weight = task.testCases.map((testCase) => testCase.weight ?? 0).reduce(this.sum);
        task.bonusMultiplier = this.getSingleValue(task.testCases.map((testCase) => testCase.bonusMultiplier));
        task.bonusPoints = task.testCases.map((testCase) => testCase.bonusPoints ?? 0).reduce(this.sum);
        task.visibility = this.getSingleValue(task.testCases.map((testCase) => testCase.visibility)) ?? TaskAdditionalEnum.Mixed;
        task.type = this.getSingleValue(task.testCases.map((testCase) => testCase.type)) ?? TaskAdditionalEnum.Mixed;
        return task;
    }

    /**
     * Gets a single value from a list if there is only one unique value. otherwise returns undefined
     * @param values
     * @private
     */
    private getSingleValue(values: any[]) {
        const set = new Set(values);
        if (set.size == 1) {
            return set.values().next().value;
        }
    }

    private sum = (a: number, b: number) => a + b;
}
