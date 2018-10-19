import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IQuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';

type EntityResponseType = HttpResponse<IQuizPointStatistic>;
type EntityArrayResponseType = HttpResponse<IQuizPointStatistic[]>;

@Injectable({ providedIn: 'root' })
export class QuizPointStatisticService {
    public resourceUrl = SERVER_API_URL + 'api/quiz-point-statistics';

    constructor(private http: HttpClient) {}

    create(quizPointStatistic: IQuizPointStatistic): Observable<EntityResponseType> {
        return this.http.post<IQuizPointStatistic>(this.resourceUrl, quizPointStatistic, { observe: 'response' });
    }

    update(quizPointStatistic: IQuizPointStatistic): Observable<EntityResponseType> {
        return this.http.put<IQuizPointStatistic>(this.resourceUrl, quizPointStatistic, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IQuizPointStatistic>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IQuizPointStatistic[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
