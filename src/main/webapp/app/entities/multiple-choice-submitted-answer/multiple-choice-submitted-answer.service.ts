import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { MultipleChoiceSubmittedAnswer } from './multiple-choice-submitted-answer.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<MultipleChoiceSubmittedAnswer>;

@Injectable()
export class MultipleChoiceSubmittedAnswerService {

    private resourceUrl =  SERVER_API_URL + 'api/multiple-choice-submitted-answers';

    constructor(private http: HttpClient) { }

    create(multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswer): Observable<EntityResponseType> {
        const copy = this.convert(multipleChoiceSubmittedAnswer);
        return this.http.post<MultipleChoiceSubmittedAnswer>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswer): Observable<EntityResponseType> {
        const copy = this.convert(multipleChoiceSubmittedAnswer);
        return this.http.put<MultipleChoiceSubmittedAnswer>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<MultipleChoiceSubmittedAnswer>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<MultipleChoiceSubmittedAnswer[]>> {
        const options = createRequestOption(req);
        return this.http.get<MultipleChoiceSubmittedAnswer[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<MultipleChoiceSubmittedAnswer[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: MultipleChoiceSubmittedAnswer = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<MultipleChoiceSubmittedAnswer[]>): HttpResponse<MultipleChoiceSubmittedAnswer[]> {
        const jsonResponse: MultipleChoiceSubmittedAnswer[] = res.body;
        const body: MultipleChoiceSubmittedAnswer[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to MultipleChoiceSubmittedAnswer.
     */
    private convertItemFromServer(multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswer): MultipleChoiceSubmittedAnswer {
        const copy: MultipleChoiceSubmittedAnswer = Object.assign({}, multipleChoiceSubmittedAnswer);
        return copy;
    }

    /**
     * Convert a MultipleChoiceSubmittedAnswer to a JSON which can be sent to the server.
     */
    private convert(multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswer): MultipleChoiceSubmittedAnswer {
        const copy: MultipleChoiceSubmittedAnswer = Object.assign({}, multipleChoiceSubmittedAnswer);
        return copy;
    }
}
