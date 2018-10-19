import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IMultipleChoiceQuestionStatistic } from 'app/shared/model/multiple-choice-question-statistic.model';

type EntityResponseType = HttpResponse<IMultipleChoiceQuestionStatistic>;
type EntityArrayResponseType = HttpResponse<IMultipleChoiceQuestionStatistic[]>;

@Injectable({ providedIn: 'root' })
export class MultipleChoiceQuestionStatisticService {
    public resourceUrl = SERVER_API_URL + 'api/multiple-choice-question-statistics';

    constructor(private http: HttpClient) {}

    create(multipleChoiceQuestionStatistic: IMultipleChoiceQuestionStatistic): Observable<EntityResponseType> {
        return this.http.post<IMultipleChoiceQuestionStatistic>(this.resourceUrl, multipleChoiceQuestionStatistic, { observe: 'response' });
    }

    update(multipleChoiceQuestionStatistic: IMultipleChoiceQuestionStatistic): Observable<EntityResponseType> {
        return this.http.put<IMultipleChoiceQuestionStatistic>(this.resourceUrl, multipleChoiceQuestionStatistic, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IMultipleChoiceQuestionStatistic>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IMultipleChoiceQuestionStatistic[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
