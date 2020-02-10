import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import { ModelingSubmission } from './modeling-submission.model';
import { createRequestOption } from 'app/shared';
import { stringifyCircular } from 'app/shared/util/utils';

export type EntityResponseType = HttpResponse<ModelingSubmission>;

@Injectable({ providedIn: 'root' })
export class ModelingSubmissionService {
    private resourceUrl = SERVER_API_URL + 'api';

    constructor(private http: HttpClient) {}

    create(modelingSubmission: ModelingSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(modelingSubmission);
        return this.http
            .post<ModelingSubmission>(`api/exercises/${exerciseId}/modeling-submissions`, copy, {
                observe: 'response',
            })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(modelingSubmission: ModelingSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(modelingSubmission);
        return this.http
            .put<ModelingSubmission>(`api/exercises/${exerciseId}/modeling-submissions`, stringifyCircular(copy), {
                headers: { 'Content-Type': 'application/json' },
                observe: 'response',
            })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    getModelingSubmissionsForExercise(exerciseId: number, req?: any): Observable<HttpResponse<ModelingSubmission[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<ModelingSubmission[]>(`${this.resourceUrl}/exercises/${exerciseId}/modeling-submissions`, {
                params: options,
                observe: 'response',
            })
            .map((res: HttpResponse<ModelingSubmission[]>) => this.convertArrayResponse(res));
    }

    getModelingSubmissionForExerciseWithoutAssessment(exerciseId: number, lock?: boolean): Observable<ModelingSubmission> {
        let url = `api/exercises/${exerciseId}/modeling-submission-without-assessment`;
        if (lock) {
            url += '?lock=true';
        }
        return this.http.get<ModelingSubmission>(url);
    }

    getSubmission(submissionId: number): Observable<ModelingSubmission> {
        return this.http.get<ModelingSubmission>(`api/modeling-submissions/${submissionId}`);
    }

    // TODO CZ: change name + change URL?
    getDataForModelingEditor(participationId: number): Observable<ModelingSubmission> {
        return this.http.get<ModelingSubmission>(`api/modeling-editor/${participationId}`, { responseType: 'json' });
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: ModelingSubmission = this.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<ModelingSubmission[]>): HttpResponse<ModelingSubmission[]> {
        const jsonResponse: ModelingSubmission[] = res.body!;
        const body: ModelingSubmission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to ModelingSubmission.
     */
    private convertItemFromServer(modelingSubmission: ModelingSubmission): ModelingSubmission {
        const copy: ModelingSubmission = Object.assign({}, modelingSubmission);
        return copy;
    }

    /**
     * Convert a ModelingSubmission to a JSON which can be sent to the server.
     */
    private convert(modelingSubmission: ModelingSubmission): ModelingSubmission {
        const copy: ModelingSubmission = Object.assign({}, modelingSubmission);
        return copy;
    }
}
