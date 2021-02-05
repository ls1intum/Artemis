import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';
import { map } from 'rxjs/operators';

import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { createRequestOption } from 'app/shared/util/request-util';
import { stringifyCircular } from 'app/shared/util/utils';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';

export type EntityResponseType = HttpResponse<ModelingSubmission>;

@Injectable({ providedIn: 'root' })
export class ModelingSubmissionService {
    public resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

    /**
     * Create a new modeling submission
     * @param {ModelingSubmission} modelingSubmission - New submission to be created
     * @param {number} exerciseId - Id of the exercise, for which the submission is made
     */
    create(modelingSubmission: ModelingSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = ModelingSubmissionService.convert(modelingSubmission);
        return this.http
            .post<ModelingSubmission>(`api/exercises/${exerciseId}/modeling-submissions`, stringifyCircular(copy), {
                headers: { 'Content-Type': 'application/json' }, // needed due to stringifyCircular
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Update an existing modeling submission
     * @param {ModelingSubmission} modelingSubmission - Updated submission
     * @param {number} exerciseId - Id of the exercise, for which the submission is made
     */
    update(modelingSubmission: ModelingSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = ModelingSubmissionService.convert(modelingSubmission);
        return this.http
            .put<ModelingSubmission>(`api/exercises/${exerciseId}/modeling-submissions`, stringifyCircular(copy), {
                headers: { 'Content-Type': 'application/json' }, // needed due to stringifyCircular
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.convertResponse(res)));
    }

    /**
     * Get all submissions for an exercise
     * @param {number} exerciseId - Id of the exercise
     * @param {any?} req - Request option
     * @param correctionRound correctionRound for which to get the Submissions
     */
    getModelingSubmissionsForExerciseByCorrectionRound(exerciseId: number, req?: any, correctionRound = 0): Observable<HttpResponse<ModelingSubmission[]>> {
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
            .pipe(map((res: HttpResponse<ModelingSubmission[]>) => this.convertArrayResponse(res)));
    }

    /**
     * Get an unassessed modeling exercise for an exercise
     * @param {number} exerciseId - Id of the exercise
     * @param {boolean?} lock - True if assessment is locked
     * @param correctionRound correctionRound for which to get the Submissions
     */
    getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment(exerciseId: number, lock?: boolean, correctionRound = 0): Observable<ModelingSubmission> {
        const url = `api/exercises/${exerciseId}/modeling-submission-without-assessment`;
        let params = new HttpParams();
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        if (lock) {
            params = params.set('lock', 'true');
        }
        return this.http
            .get<ModelingSubmission>(url, { params })
            .pipe(map((res: ModelingSubmission) => ModelingSubmissionService.convertItemFromServer(res)));
    }

    /**
     * Get a submission with given Id
     * @param {number} submissionId - Id of the submission
     * @param correctionRound
     */
    getSubmission(submissionId: number, correctionRound = 0): Observable<ModelingSubmission> {
        const url = `api/modeling-submissions/${submissionId}`;
        let params = new HttpParams();
        if (correctionRound !== 0) {
            params = params.set('correction-round', correctionRound.toString());
        }
        return this.http.get<ModelingSubmission>(url, { params });
    }

    /**
     * Get latest submission for a given participation
     * @param {number} participationId - Id of the participation
     */
    getLatestSubmissionForModelingEditor(participationId: number): Observable<ModelingSubmission> {
        return this.http.get<ModelingSubmission>(`api/participations/${participationId}/latest-modeling-submission`, { responseType: 'json' });
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: ModelingSubmission = ModelingSubmissionService.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<ModelingSubmission[]>): HttpResponse<ModelingSubmission[]> {
        const jsonResponse: ModelingSubmission[] = res.body!;
        const body: ModelingSubmission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(ModelingSubmissionService.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to ModelingSubmission.
     */
    private static convertItemFromServer(modelingSubmission: ModelingSubmission): ModelingSubmission {
        const convertedModelingSubmission = Object.assign({}, modelingSubmission);
        setLatestSubmissionResult(convertedModelingSubmission, getLatestSubmissionResult(convertedModelingSubmission));
        return convertedModelingSubmission;
    }

    /**
     * Convert a ModelingSubmission to a JSON which can be sent to the server.
     */
    private static convert(modelingSubmission: ModelingSubmission): ModelingSubmission {
        return Object.assign({}, modelingSubmission);
    }
}
