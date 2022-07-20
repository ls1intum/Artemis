import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SubmissionPolicy } from 'app/entities/submission-policy.model';

export interface ISubmissionPolicyService {
    addSubmissionPolicyToProgrammingExercise: (submissionPolicy: SubmissionPolicy, exerciseId: number) => Observable<SubmissionPolicy>;
    getSubmissionPolicyOfProgrammingExercise: (exerciseId: number) => Observable<SubmissionPolicy>;
    removeSubmissionPolicyFromProgrammingExercise: (exerciseId: number) => Observable<HttpResponse<void>>;
    enableSubmissionPolicyOfProgrammingExercise: (exerciseId: number) => Observable<HttpResponse<void>>;
    disableSubmissionPolicyOfProgrammingExercise: (exerciseId: number) => Observable<HttpResponse<void>>;
    updateSubmissionPolicyToProgrammingExercise: (submissionPolicy: SubmissionPolicy, exerciseId: number) => Observable<SubmissionPolicy>;
}

@Injectable({ providedIn: 'root' })
export class SubmissionPolicyService implements ISubmissionPolicyService {
    public baseResourceUrl = SERVER_API_URL + 'api/programming-exercises/{exerciseId}/submission-policy';

    constructor(private http: HttpClient) {}

    /**
     * Returns the observable of the submission policy of the programming exercise with the id that
     * is passed as argument.
     *
     * @param exerciseId of the programming exercise for which the submission policy should be loaded
     */
    getSubmissionPolicyOfProgrammingExercise(exerciseId: number): Observable<SubmissionPolicy> {
        return this.http.get<SubmissionPolicy>(this.requestUrl(exerciseId));
    }

    /**
     * Adds the passed submission policy to the programming exercise with the passed id and returns an
     * observable of the added submission policy.
     *
     * @param submissionPolicy that should be added to the programming exercise
     * @param exerciseId of the programming exercise to which the passed submission policy should be added
     */
    addSubmissionPolicyToProgrammingExercise(submissionPolicy: SubmissionPolicy, exerciseId: number): Observable<SubmissionPolicy> {
        submissionPolicy.active = false;
        return this.http.post<SubmissionPolicy>(this.requestUrl(exerciseId), submissionPolicy);
    }

    /**
     * Removes the submission policy of the programming exercise with the passed exerciseId and
     * returns an observable of the http response.
     *
     * @param exerciseId of the programming exercise from which the submission policy should be removed
     */
    removeSubmissionPolicyFromProgrammingExercise(exerciseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(this.requestUrl(exerciseId), { observe: 'response' });
    }

    /**
     * Activates the submission policy of the programming exercise with the passed exerciseId and
     * returns an observable of the http response.
     *
     * @param exerciseId of the programming exercise for which the submission policy should be activated
     */
    enableSubmissionPolicyOfProgrammingExercise(exerciseId: number): Observable<HttpResponse<void>> {
        const params = new HttpParams().set('activate', 'true');
        return this.http.put<void>(this.requestUrl(exerciseId), null, { observe: 'response', params });
    }

    /**
     * Deactivates the submission policy of the programming exercise with the passed exerciseId and
     * returns an observable of the http response.
     *
     * @param exerciseId of the programming exercise for which the submission policy should be deactivated
     */
    disableSubmissionPolicyOfProgrammingExercise(exerciseId: number): Observable<HttpResponse<void>> {
        const params = new HttpParams().set('activate', 'false');
        return this.http.put<void>(this.requestUrl(exerciseId), null, { observe: 'response', params });
    }

    /**
     * Updates the submission policy to the programming exercise with the passed id and returns an
     * observable of the new submission policy.
     *
     * @param submissionPolicy that should be the new submission policy of the programming exercise
     * @param exerciseId of the programming exercise of which the submission policy should be updated
     */
    updateSubmissionPolicyToProgrammingExercise(submissionPolicy: SubmissionPolicy, exerciseId: number): Observable<SubmissionPolicy> {
        return this.http.patch<SubmissionPolicy>(this.requestUrl(exerciseId), submissionPolicy);
    }

    /**
     * Returns the appropriate request URL for a given exerciseId.
     *
     * @param exerciseId that is to be included in the request URL
     * @private
     */
    private requestUrl(exerciseId: number): string {
        return this.baseResourceUrl.replace('{exerciseId}', exerciseId + '');
    }
}
