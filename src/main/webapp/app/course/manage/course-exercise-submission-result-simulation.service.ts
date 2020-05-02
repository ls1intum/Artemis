import { Injectable } from '@angular/core';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { Observable } from 'rxjs/Observable';
import { HttpResponse, HttpClient } from '@angular/common/http';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

/**
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */

@Injectable({ providedIn: 'root' })
export class CourseExerciseSubmissionResultSimulationService {
    constructor(private http: HttpClient) {}

    simulateSubmission(exerciseId: number): Observable<HttpResponse<ProgrammingSubmission>> {
        return this.http.post<ProgrammingSubmission>(`api/exercises/${exerciseId}/submissions/no-vcs-and-ci-available`, {}, { observe: 'response' });
    }

    simulateResult(exerciseId: number): Observable<HttpResponse<Result>> {
        return this.http.post<Result>(`api/exercises/${exerciseId}/results/no-vcs-and-ci-available`, {}, { observe: 'response' });
    }
}
