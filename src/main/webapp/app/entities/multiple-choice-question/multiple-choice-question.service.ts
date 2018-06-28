import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { MultipleChoiceQuestion } from './multiple-choice-question.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<MultipleChoiceQuestion>;

@Injectable()
export class MultipleChoiceQuestionService {

    private resourceUrl =  SERVER_API_URL + 'api/multiple-choice-questions';

    constructor(private http: HttpClient) { }

    create(multipleChoiceQuestion: MultipleChoiceQuestion): Observable<EntityResponseType> {
        const copy = this.convert(multipleChoiceQuestion);
        return this.http.post<MultipleChoiceQuestion>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(multipleChoiceQuestion: MultipleChoiceQuestion): Observable<EntityResponseType> {
        const copy = this.convert(multipleChoiceQuestion);
        return this.http.put<MultipleChoiceQuestion>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<MultipleChoiceQuestion>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<MultipleChoiceQuestion[]>> {
        const options = createRequestOption(req);
        return this.http.get<MultipleChoiceQuestion[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<MultipleChoiceQuestion[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: MultipleChoiceQuestion = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<MultipleChoiceQuestion[]>): HttpResponse<MultipleChoiceQuestion[]> {
        const jsonResponse: MultipleChoiceQuestion[] = res.body;
        const body: MultipleChoiceQuestion[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to MultipleChoiceQuestion.
     */
    private convertItemFromServer(multipleChoiceQuestion: MultipleChoiceQuestion): MultipleChoiceQuestion {
        const copy: MultipleChoiceQuestion = Object.assign({}, multipleChoiceQuestion);
        return copy;
    }

    /**
     * Convert a MultipleChoiceQuestion to a JSON which can be sent to the server.
     */
    private convert(multipleChoiceQuestion: MultipleChoiceQuestion): MultipleChoiceQuestion {
        const copy: MultipleChoiceQuestion = Object.assign({}, multipleChoiceQuestion);
        return copy;
    }
}
