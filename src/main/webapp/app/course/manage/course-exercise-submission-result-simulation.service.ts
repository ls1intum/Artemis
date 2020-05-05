import { Injectable } from '@angular/core';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { Observable } from 'rxjs/Observable';
import { HttpResponse, HttpClient } from '@angular/common/http';

/**
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */

@Injectable({ providedIn: 'root' })
export class CourseExerciseSubmissionResultSimulationService {
    constructor(private http: HttpClient) {}

    /**
     * Simulate a submission to a programming exercise (only for testing purposes noVersionControlAndContinuousIntegrationAvailable).
     * @param exerciseId Id of the exercise to submit to.
     */
    simulateSubmission(exerciseId: number): Observable<HttpResponse<ProgrammingSubmission>> {
        return this.http.post<ProgrammingSubmission>(`api/exercises/${exerciseId}/submissions/no-vcs-and-ci-available`, {}, { observe: 'response' });
    }

    /**
     * Simulate a result of a programming exercise (only for testing purposes noVersionControlAndContinuousIntegrationAvailable).
     * @param exerciseId Id of the exercise the result is for.
     */
    simulateResult(exerciseId: number): Observable<HttpResponse<Result>> {
        return this.http.post<Result>(`api/exercises/${exerciseId}/results/no-vcs-and-ci-available`, {}, { observe: 'response' });
    }
}
