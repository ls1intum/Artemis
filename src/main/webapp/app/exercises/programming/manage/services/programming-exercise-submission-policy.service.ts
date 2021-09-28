import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
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
    public enableResourceUrl = this.baseResourceUrl + '/enable';
    public disableResourceUrl = this.baseResourceUrl + '/disable';
    public updateResourceUrl = this.baseResourceUrl + '/update';

    constructor(private http: HttpClient) {}

    getSubmissionPolicyOfProgrammingExercise(exerciseId: number): Observable<SubmissionPolicy> {
        return this.http.get<SubmissionPolicy>(this.baseResourceUrl.replace('{exerciseId}', exerciseId + ''));
    }

    addSubmissionPolicyToProgrammingExercise(submissionPolicy: SubmissionPolicy, exerciseId: number): Observable<SubmissionPolicy> {
        return this.http.post<SubmissionPolicy>(this.baseResourceUrl.replace('{exerciseId}', exerciseId + ''), submissionPolicy);
    }

    removeSubmissionPolicyFromProgrammingExercise(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(this.baseResourceUrl.replace('{exerciseId}', exerciseId + ''), { observe: 'response' });
    }

    enableSubmissionPolicyOfProgrammingExercise(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.put<void>(this.enableResourceUrl.replace('{exerciseId}', exerciseId + ''), null, { observe: 'response' });
    }

    disableSubmissionPolicyOfProgrammingExercise(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.put<void>(this.disableResourceUrl.replace('{exerciseId}', exerciseId + ''), null, { observe: 'response' });
    }

    updateSubmissionPolicyToProgrammingExercise(submissionPolicy: SubmissionPolicy, exerciseId: number): Observable<SubmissionPolicy> {
        return this.http.patch<SubmissionPolicy>(this.updateResourceUrl.replace('{exerciseId}', exerciseId + ''), submissionPolicy);
    }
}
