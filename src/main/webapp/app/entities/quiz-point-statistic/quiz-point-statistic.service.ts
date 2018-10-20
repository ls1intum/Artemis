import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { QuizPointStatistic } from 'app/entities/quiz-point-statistic/quiz-point-statistic.model';

type EntityResponseType = HttpResponse<QuizPointStatistic>;
type EntityArrayResponseType = HttpResponse<QuizPointStatistic[]>;

@Injectable({ providedIn: 'root' })
export class QuizPointStatisticService {
    public resourceUrl = SERVER_API_URL + 'api/quiz-point-statistics';

    constructor(private http: HttpClient) {}

    create(quizPointStatistic: QuizPointStatistic): Observable<EntityResponseType> {
        return this.http.post<QuizPointStatistic>(this.resourceUrl, quizPointStatistic, { observe: 'response' });
    }

    update(quizPointStatistic: QuizPointStatistic): Observable<EntityResponseType> {
        return this.http.put<QuizPointStatistic>(this.resourceUrl, quizPointStatistic, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<QuizPointStatistic>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<QuizPointStatistic[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
