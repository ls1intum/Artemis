import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { SubmittedAnswer } from './submitted-answer.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<SubmittedAnswer>;

@Injectable()
export class SubmittedAnswerService {

    private resourceUrl =  SERVER_API_URL + 'api/submitted-answers';

    constructor(private http: HttpClient) { }

    create(submittedAnswer: SubmittedAnswer): Observable<EntityResponseType> {
        const copy = this.convert(submittedAnswer);
        return this.http.post<SubmittedAnswer>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(submittedAnswer: SubmittedAnswer): Observable<EntityResponseType> {
        const copy = this.convert(submittedAnswer);
        return this.http.put<SubmittedAnswer>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<SubmittedAnswer>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<SubmittedAnswer[]>> {
        const options = createRequestOption(req);
        return this.http.get<SubmittedAnswer[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<SubmittedAnswer[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: SubmittedAnswer = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<SubmittedAnswer[]>): HttpResponse<SubmittedAnswer[]> {
        const jsonResponse: SubmittedAnswer[] = res.body;
        const body: SubmittedAnswer[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to SubmittedAnswer.
     */
    private convertItemFromServer(submittedAnswer: SubmittedAnswer): SubmittedAnswer {
        const copy: SubmittedAnswer = Object.assign({}, submittedAnswer);
        return copy;
    }

    /**
     * Convert a SubmittedAnswer to a JSON which can be sent to the server.
     */
    private convert(submittedAnswer: SubmittedAnswer): SubmittedAnswer {
        const copy: SubmittedAnswer = Object.assign({}, submittedAnswer);
        return copy;
    }
}
