import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';

export interface IProgrammingExerciseSubmissionPolicyService {
    addSubmissionPolicyToProgrammingExercise: (submissionPolicy: SubmissionPolicy, exerciseId: number) => Observable<SubmissionPolicy>;
    getSubmissionPolicyOfProgrammingExercise: (exerciseId: number) => Observable<SubmissionPolicy>;
    removeSubmissionPolicyFromProgrammingExercise: (exerciseId: number) => Observable<HttpResponse<void>>;
    enableSubmissionPolicyOfProgrammingExercise: (exerciseId: number) => Observable<HttpResponse<void>>;
    disableSubmissionPolicyOfProgrammingExercise: (exerciseId: number) => Observable<HttpResponse<void>>;
    updateSubmissionPolicyToProgrammingExercise: (submissionPolicy: SubmissionPolicy, exerciseId: number) => Observable<SubmissionPolicy>;
}

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSubmissionPolicyService implements IProgrammingExerciseSubmissionPolicyService {
    public baseResourceUrl = SERVER_API_URL + '/api/programming-exercises/{exerciseId}/submission-policy';

    constructor(private http: HttpClient) {}

    getSubmissionPolicyOfProgrammingExercise(exerciseId: number): Observable<SubmissionPolicy> {
        return this.http.get<SubmissionPolicy>(this.requestUrl(exerciseId));
    }

    addSubmissionPolicyToProgrammingExercise(submissionPolicy: SubmissionPolicy, exerciseId: number): Observable<SubmissionPolicy> {
        submissionPolicy.active = false;
        return this.http.post<SubmissionPolicy>(this.requestUrl(exerciseId), submissionPolicy);
    }

    removeSubmissionPolicyFromProgrammingExercise(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(this.requestUrl(exerciseId), { observe: 'response' });
    }

    enableSubmissionPolicyOfProgrammingExercise(exerciseId: number): Observable<HttpResponse<void>> {
        const params = new HttpParams().set('activate', 'true');
        return this.http.put<void>(this.requestUrl(exerciseId), null, { observe: 'response', params });
    }

    disableSubmissionPolicyOfProgrammingExercise(exerciseId: number): Observable<HttpResponse<void>> {
        const params = new HttpParams().set('activate', 'false');
        return this.http.put<void>(this.requestUrl(exerciseId), null, { observe: 'response', params });
    }

    updateSubmissionPolicyToProgrammingExercise(submissionPolicy: SubmissionPolicy, exerciseId: number): Observable<SubmissionPolicy> {
        return this.http.patch<SubmissionPolicy>(this.requestUrl(exerciseId), submissionPolicy);
    }

    private requestUrl(exerciseId: number): string {
        return this.baseResourceUrl.replace('{exerciseId}', exerciseId + '');
    }
}
