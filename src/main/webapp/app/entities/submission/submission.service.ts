import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { Submission } from './submission.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<Submission>;

@Injectable()
export class SubmissionService {

    private resourceUrl =  SERVER_API_URL + 'api/submissions';

    constructor(private http: HttpClient) { }

    create(submission: Submission): Observable<EntityResponseType> {
        const copy = this.convert(submission);
        return this.http.post<Submission>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(submission: Submission): Observable<EntityResponseType> {
        const copy = this.convert(submission);
        return this.http.put<Submission>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<Submission>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<Submission[]>> {
        const options = createRequestOption(req);
        return this.http.get<Submission[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<Submission[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Submission = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<Submission[]>): HttpResponse<Submission[]> {
        const jsonResponse: Submission[] = res.body;
        const body: Submission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Submission.
     */
    private convertItemFromServer(submission: Submission): Submission {
        const copy: Submission = Object.assign({}, submission);
        return copy;
    }

    /**
     * Convert a Submission to a JSON which can be sent to the server.
     */
    private convert(submission: Submission): Submission {
        const copy: Submission = Object.assign({}, submission);
        return copy;
    }
}
