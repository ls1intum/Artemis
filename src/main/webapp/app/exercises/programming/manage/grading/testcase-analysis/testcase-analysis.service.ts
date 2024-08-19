import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { map } from 'rxjs/operators';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';

export interface SimplifiedTask {
    taskName: string;
    testCases: ProgrammingExerciseServerSideTask['testCases'];
}

export interface FeedbackDetailsWithResultIdsDTO {
    feedbackDetails: { detailText: string; testCaseName: string }[];
    resultIds: number[];
}

@Injectable()
export class TestcaseAnalysisService {
    private resourceUrl = 'api/programming-exercises';
    private exerciseResourceUrl = 'api/exercises';
    isAtLeastEditor = false;

    constructor(private http: HttpClient) {}

    getFeedbackDetailsForExercise(exerciseId: number): Observable<HttpResponse<FeedbackDetailsWithResultIdsDTO>> {
        if (this.isAtLeastEditor) {
            return this.http.get<FeedbackDetailsWithResultIdsDTO>(`${this.exerciseResourceUrl}/${exerciseId}/feedback-details`, { observe: 'response' });
        } else {
            return throwError(
                () =>
                    new HttpErrorResponse({
                        status: 403,
                        statusText: 'Forbidden',
                        error: 'User does not have permission to access this resource.',
                    }),
            );
        }
    }

    public getSimplifiedTasks(exerciseId: number): Observable<SimplifiedTask[]> {
        if (this.isAtLeastEditor) {
            return this.http.get<ProgrammingExerciseServerSideTask[]>(`${this.resourceUrl}/${exerciseId}/tasks-with-unassigned-test-cases`).pipe(
                map((tasks) =>
                    tasks.map((task) => ({
                        taskName: task.taskName ?? '',
                        testCases: task.testCases ?? [],
                    })),
                ),
            );
        } else {
            return throwError(
                () =>
                    new HttpErrorResponse({
                        status: 403,
                        statusText: 'Forbidden',
                        error: 'User does not have permission to access this resource.',
                    }),
            );
        }
    }
}
