import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IQuestionStatistic } from 'app/shared/model/question-statistic.model';

type EntityResponseType = HttpResponse<IQuestionStatistic>;
type EntityArrayResponseType = HttpResponse<IQuestionStatistic[]>;

@Injectable({ providedIn: 'root' })
export class QuestionStatisticService {
    public resourceUrl = SERVER_API_URL + 'api/question-statistics';

    constructor(private http: HttpClient) {}

    create(questionStatistic: IQuestionStatistic): Observable<EntityResponseType> {
        return this.http.post<IQuestionStatistic>(this.resourceUrl, questionStatistic, { observe: 'response' });
    }

    update(questionStatistic: IQuestionStatistic): Observable<EntityResponseType> {
        return this.http.put<IQuestionStatistic>(this.resourceUrl, questionStatistic, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IQuestionStatistic>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IQuestionStatistic[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
