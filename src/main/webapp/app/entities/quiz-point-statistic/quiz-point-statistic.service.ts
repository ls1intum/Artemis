import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { QuizPointStatistic } from './quiz-point-statistic.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<QuizPointStatistic>;

@Injectable()
export class QuizPointStatisticService {
    private resourceUrl = SERVER_API_URL + 'api/quiz-point-statistics';

    constructor(private http: HttpClient) {}

    create(quizPointStatistic: QuizPointStatistic): Observable<EntityResponseType> {
        const copy = this.convert(quizPointStatistic);
        return this.http
            .post<QuizPointStatistic>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(quizPointStatistic: QuizPointStatistic): Observable<EntityResponseType> {
        const copy = this.convert(quizPointStatistic);
        return this.http
            .put<QuizPointStatistic>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizPointStatistic>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<QuizPointStatistic[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<QuizPointStatistic[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<QuizPointStatistic[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: QuizPointStatistic = this.convertItemFromServer(res.body);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<QuizPointStatistic[]>): HttpResponse<QuizPointStatistic[]> {
        const jsonResponse: QuizPointStatistic[] = res.body;
        const body: QuizPointStatistic[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to QuizPointStatistic.
     */
    private convertItemFromServer(quizPointStatistic: QuizPointStatistic): QuizPointStatistic {
        const copy: QuizPointStatistic = Object.assign({}, quizPointStatistic);
        return copy;
    }

    /**
     * Convert a QuizPointStatistic to a JSON which can be sent to the server.
     */
    private convert(quizPointStatistic: QuizPointStatistic): QuizPointStatistic {
        const copy: QuizPointStatistic = Object.assign({}, quizPointStatistic);
        return copy;
    }
}
