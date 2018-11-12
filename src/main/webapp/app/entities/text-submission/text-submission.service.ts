import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from 'app/app.constants';

import { createRequestOption } from 'app/shared';
import { TextSubmission } from './text-submission.model';

export type EntityResponseType = HttpResponse<TextSubmission>;

@Injectable({ providedIn: 'root' })
export class TextSubmissionService {
    private resourceUrl = SERVER_API_URL + 'api/text-submissions';

    constructor(private http: HttpClient) {}

    create(textSubmission: TextSubmission, exerciseId?: number): Observable<EntityResponseType> {
        const copy = this.convert(textSubmission);
        if (exerciseId) {
            return this.http
                .post<TextSubmission>(`api/exercises/${exerciseId}/text-submissions`, copy, {
                    observe: 'response'
                })
                .map((res: EntityResponseType) => this.convertResponse(res));
        }
        return this.http
            .post<TextSubmission>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(textSubmission: TextSubmission, exerciseId?: number): Observable<EntityResponseType> {
        const copy = this.convert(textSubmission);
        if (exerciseId) {
            return this.http
                .put<TextSubmission>(`api/exercises/${exerciseId}/text-submissions`, copy, {
                    observe: 'response'
                })
                .map((res: EntityResponseType) => this.convertResponse(res));
        }
        return this.http
            .put<TextSubmission>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<TextSubmission>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<TextSubmission[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<TextSubmission[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<TextSubmission[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: TextSubmission = this.convertItemFromServer(res.body);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<TextSubmission[]>): HttpResponse<TextSubmission[]> {
        const jsonResponse: TextSubmission[] = res.body;
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
