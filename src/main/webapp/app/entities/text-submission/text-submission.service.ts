import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { TextSubmission } from './text-submission.model';
import { createRequestOption } from 'app/shared';
import { stringifyCircular } from 'app/shared/util/utils';

export type EntityResponseType = HttpResponse<TextSubmission>;

@Injectable({ providedIn: 'root' })
export class TextSubmissionService {
    constructor(private http: HttpClient) {}

    create(textSubmission: TextSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(textSubmission);
        return this.http
            .post<TextSubmission>(`api/exercises/${exerciseId}/text-submissions`, copy, {
                observe: 'response',
            })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(textSubmission: TextSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(textSubmission);
        return this.http
            .put<TextSubmission>(`api/exercises/${exerciseId}/text-submissions`, stringifyCircular(copy), {
                headers: { 'Content-Type': 'application/json' },
                observe: 'response',
            })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    get(textSubmissionId: number): Observable<HttpResponse<TextSubmission>> {
        return this.http
            .get<TextSubmission>(`api/text-submissions/${textSubmissionId}`, {
                observe: 'response',
            })
            .map((res: HttpResponse<TextSubmission>) => this.convertResponse(res));
    }

    getTextSubmissionsForExercise(exerciseId: number, req: { submittedOnly?: boolean; assessedByTutor?: boolean }): Observable<HttpResponse<TextSubmission[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<TextSubmission[]>(`api/exercises/${exerciseId}/text-submissions`, {
                params: options,
                observe: 'response',
            })
            .map((res: HttpResponse<TextSubmission[]>) => this.convertArrayResponse(res));
    }

    getTextSubmissionForExerciseWithoutAssessment(exerciseId: number): Observable<TextSubmission> {
        return this.http.get<TextSubmission>(`api/exercises/${exerciseId}/text-submission-without-assessment`);
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: TextSubmission = this.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<TextSubmission[]>): HttpResponse<TextSubmission[]> {
        const jsonResponse: TextSubmission[] = res.body!;
        const body: TextSubmission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to TextSubmission.
     */
    private convertItemFromServer(textSubmission: TextSubmission): TextSubmission {
        return Object.assign({}, textSubmission);
    }

    /**
     * Convert a TextSubmission to a JSON which can be sent to the server.
     */
    private convert(textSubmission: TextSubmission): TextSubmission {
        return Object.assign({}, textSubmission);
    }
}
