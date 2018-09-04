import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { QuestionStatistic } from './question-statistic.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<QuestionStatistic>;

@Injectable()
export class QuestionStatisticService {

    private resourceUrl =  SERVER_API_URL + 'api/question-statistics';

    constructor(private http: HttpClient) { }

    create(questionStatistic: QuestionStatistic): Observable<EntityResponseType> {
        const copy = this.convert(questionStatistic);
        return this.http.post<QuestionStatistic>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(questionStatistic: QuestionStatistic): Observable<EntityResponseType> {
        const copy = this.convert(questionStatistic);
        return this.http.put<QuestionStatistic>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<QuestionStatistic>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<QuestionStatistic[]>> {
        const options = createRequestOption(req);
        return this.http.get<QuestionStatistic[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<QuestionStatistic[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: QuestionStatistic = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<QuestionStatistic[]>): HttpResponse<QuestionStatistic[]> {
        const jsonResponse: QuestionStatistic[] = res.body;
        const body: QuestionStatistic[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to QuestionStatistic.
     */
    private convertItemFromServer(questionStatistic: QuestionStatistic): QuestionStatistic {
        const copy: QuestionStatistic = Object.assign({}, questionStatistic);
        return copy;
    }

    /**
     * Convert a QuestionStatistic to a JSON which can be sent to the server.
     */
    private convert(questionStatistic: QuestionStatistic): QuestionStatistic {
        const copy: QuestionStatistic = Object.assign({}, questionStatistic);
        return copy;
    }
}
