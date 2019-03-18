import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { QuizQuestionStatistic } from './quiz-question-statistic.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<QuizQuestionStatistic>;

@Injectable({ providedIn: 'root' })
export class QuizQuestionStatisticService {
    private resourceUrl = SERVER_API_URL + 'api/question-statistics';

    constructor(private http: HttpClient) {}

    create(questionStatistic: QuizQuestionStatistic): Observable<EntityResponseType> {
        const copy = this.convert(questionStatistic);
        return this.http
            .post<QuizQuestionStatistic>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(questionStatistic: QuizQuestionStatistic): Observable<EntityResponseType> {
        const copy = this.convert(questionStatistic);
        return this.http
            .put<QuizQuestionStatistic>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizQuestionStatistic>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<QuizQuestionStatistic[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<QuizQuestionStatistic[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<QuizQuestionStatistic[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: QuizQuestionStatistic = this.convertItemFromServer(res.body);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<QuizQuestionStatistic[]>): HttpResponse<QuizQuestionStatistic[]> {
        const jsonResponse: QuizQuestionStatistic[] = res.body;
        const body: QuizQuestionStatistic[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to QuizQuestionStatistic.
     */
    private convertItemFromServer(questionStatistic: QuizQuestionStatistic): QuizQuestionStatistic {
        const copy: QuizQuestionStatistic = Object.assign({}, questionStatistic);
        return copy;
    }

    /**
     * Convert a QuizQuestionStatistic to a JSON which can be sent to the server.
     */
    private convert(questionStatistic: QuizQuestionStatistic): QuizQuestionStatistic {
        const copy: QuizQuestionStatistic = Object.assign({}, questionStatistic);
        return copy;
    }
}
