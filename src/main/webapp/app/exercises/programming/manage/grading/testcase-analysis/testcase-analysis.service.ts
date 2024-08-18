import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';

export interface SimplifiedTask {
    taskName: string;
    testCases: ProgrammingExerciseServerSideTask['testCases'];
}

@Injectable()
export class TestcaseAnalysisService {
    public resourceUrl = 'api/programming-exercises';

    constructor(private http: HttpClient) {}

    public getSimplifiedTasks(exerciseId: number): Observable<SimplifiedTask[]> {
        return this.http.get<ProgrammingExerciseServerSideTask[]>(`${this.resourceUrl}/${exerciseId}/tasks-with-unassigned-test-cases`).pipe(
            map((tasks) =>
                tasks.map((task) => ({
                    taskName: task.taskName ?? '',
                    testCases: task.testCases ?? [],
                })),
            ),
        );
    }
}
