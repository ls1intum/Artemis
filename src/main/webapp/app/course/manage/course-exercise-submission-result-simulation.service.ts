import { SERVER_API_URL } from 'app/app.constants';
import { Injectable } from '@angular/core';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { Observable } from 'rxjs/Observable';
import { HttpResponse, HttpClient } from '@angular/common/http';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class CourseExerciseSubmissionResultSimulationService {
    private resourceUrlWithoutLocalSetup = SERVER_API_URL + `api`;

    constructor(private http: HttpClient) {}

    simulateSubmission(exerciseId: number): Observable<HttpResponse<ProgrammingSubmission>> {
        return this.http.post<ProgrammingSubmission>(`${this.resourceUrlWithoutLocalSetup}/submissions/no-local-setup/${exerciseId}`, {}, { observe: 'response' });
    }

    simulateResult(exerciseId: number): Observable<HttpResponse<Result>> {
        return this.http.post<Result>(`${this.resourceUrlWithoutLocalSetup}/results/no-local-setup/${exerciseId}`, {}, { observe: 'response' });
    }

    getProgrammingExercise(exerciseId: number): Observable<ProgrammingExercise> {
        return this.http.get<ProgrammingExercise>(`${this.resourceUrlWithoutLocalSetup}/programming-exercises/${exerciseId}`);
    }
}
