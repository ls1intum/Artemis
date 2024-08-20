import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { FeedbackDetail } from 'app/exercises/programming/manage/grading/feedback-analysis/feedback-analysis.component';

export interface SimplifiedTask {
    taskName: string;
    testCases: ProgrammingExerciseServerSideTask['testCases'];
}

export interface FeedbackDetailsWithResultIdsDTO {
    feedbackDetails: FeedbackDetail[];
    resultIds: number[];
}

@Injectable()
export class FeedbackAnalysisService {
    private readonly resourceUrl = 'api/programming-exercises';
    private readonly exerciseResourceUrl = 'api/exercises';

    constructor(private http: HttpClient) {}

    getFeedbackDetailsForExercise(exerciseId: number): Observable<HttpResponse<FeedbackDetailsWithResultIdsDTO>> {
        return this.http.get<FeedbackDetailsWithResultIdsDTO>(`${this.exerciseResourceUrl}/${exerciseId}/feedback-details`, { observe: 'response' });
    }

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
