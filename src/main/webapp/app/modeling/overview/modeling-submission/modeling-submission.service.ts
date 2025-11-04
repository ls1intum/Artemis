import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { stringifyCircular } from 'app/shared/util/utils';
import { SubmissionService } from 'app/exercise/submission/submission.service';

export type EntityResponseType = HttpResponse<ModelingSubmission>;

@Injectable({ providedIn: 'root' })
export class ModelingSubmissionService {
    private http = inject(HttpClient);
    private submissionService = inject(SubmissionService);

    public resourceUrl = 'api/modeling';

    /**
     * Create a new modeling submission
     * @param modelingSubmission - New submission to be created
     * @param exerciseId - Id of the exercise, for which the submission is made
     */
    create(modelingSubmission: ModelingSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(modelingSubmission);
        return this.http
            .post<ModelingSubmission>(`${this.resourceUrl}/exercises/${exerciseId}/modeling-submissions`, stringifyCircular(copy), {
                headers: { 'Content-Type': 'application/json' }, // needed due to stringifyCircular
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    /**
     * Update an existing modeling submission
     * @param modelingSubmission - Updated submission
     * @param exerciseId - Id of the exercise, for which the submission is made
     */
    update(modelingSubmission: ModelingSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(modelingSubmission);
        return this.http
            .put<ModelingSubmission>(`${this.resourceUrl}/exercises/${exerciseId}/modeling-submissions`, stringifyCircular(copy), {
                headers: { 'Content-Type': 'application/json' }, // needed due to stringifyCircular
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }

    /**
     * Get all submissions for an exercise
     * @param exerciseId - Id of the exercise
     * @param req - Request option
     * @param correctionRound correctionRound for which to get the Submissions
     */
    getSubmissions(exerciseId: number, req?: any, correctionRound = 0): Observable<HttpResponse<ModelingSubmission[]>> {
        const url = `${this.resourceUrl}/exercises/${exerciseId}/modeling-submissions`;
        let params = createRequestOption(req);
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        return this.http
            .get<ModelingSubmission[]>(url, {
                params,
                observe: 'response',
            })
            .pipe(map((res: HttpResponse<ModelingSubmission[]>) => this.submissionService.convertArrayResponse(res)));
    }

    /**
     * Get an unassessed modeling exercise for an exercise
     * @param exerciseId - Id of the exercise
     * @param {boolean?} lock - True if assessment is locked
     * @param correctionRound correctionRound for which to get the Submissions
     */
    getSubmissionWithoutAssessment(exerciseId: number, lock?: boolean, correctionRound = 0): Observable<ModelingSubmission | undefined> {
        const url = `${this.resourceUrl}/exercises/${exerciseId}/modeling-submission-without-assessment`;
        let params = new HttpParams();
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        if (lock) {
            params = params.set('lock', 'true');
        }
        return this.http.get<ModelingSubmission | undefined>(url, { params }).pipe(
            map((res?: ModelingSubmission) => {
                if (!res) {
                    return undefined;
                }
                return this.submissionService.convertSubmissionFromServer(res);
            }),
        );
    }

    /**
     * Get a submission with given Id
     * @param submissionId - Id of the submission
     * @param correctionRound
     * @param resultId
     */
    getSubmission(submissionId: number, correctionRound = 0, resultId?: number): Observable<ModelingSubmission> {
        const url = `${this.resourceUrl}/modeling-submissions/${submissionId}`;
        let params = new HttpParams();
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        if (resultId && resultId > 0) {
            params = params.set('resultId', resultId.toString());
        }
        return this.http.get<ModelingSubmission>(url, { params }).pipe(map((res: ModelingSubmission) => this.submissionService.convertSubmissionFromServer(res)));
    }

    /**
     * Get a submission with given Id without locking it on artemis
     * @param submissionId - Id of the submission
     */
    getSubmissionWithoutLock(submissionId: number): Observable<ModelingSubmission> {
        const url = `${this.resourceUrl}/modeling-submissions/${submissionId}`;
        let params = new HttpParams();
        params = params.set('withoutResults', 'true');
        return this.http.get<ModelingSubmission>(url, { params }).pipe(map((res: ModelingSubmission) => this.submissionService.convertSubmissionFromServer(res)));
    }

    /**
     * Get the latest submission for a given participation
     * @param participationId - Id of the participation
     */
    getLatestSubmissionForModelingEditor(participationId: number): Observable<ModelingSubmission> {
        const params = new HttpParams().set('withAthenaConfig', 'true');
        return this.http
            .get<ModelingSubmission>(`${this.resourceUrl}/participations/${participationId}/latest-modeling-submission`, { responseType: 'json', params })
            .pipe(map((res: ModelingSubmission) => this.submissionService.convertSubmissionFromServer(res)));
    }

    /**
     * Get all submissions with results for a participation
     * @param participationId - Id of the participation
     */
    getSubmissionsWithResultsForParticipation(participationId: number): Observable<ModelingSubmission[]> {
        const url = `${this.resourceUrl}/participations/${participationId}/submissions-with-results`;
        return this.http.get<ModelingSubmission[]>(url).pipe(
            map((submissions: ModelingSubmission[]) => {
                return submissions.map((submission) => this.submissionService.convertSubmissionFromServer(submission) as ModelingSubmission);
            }),
        );
    }
}
