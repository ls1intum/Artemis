import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IDragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';

type EntityResponseType = HttpResponse<IDragAndDropQuestionStatistic>;
type EntityArrayResponseType = HttpResponse<IDragAndDropQuestionStatistic[]>;

@Injectable({ providedIn: 'root' })
export class DragAndDropQuestionStatisticService {
    public resourceUrl = SERVER_API_URL + 'api/drag-and-drop-question-statistics';

    constructor(private http: HttpClient) {}

    create(dragAndDropQuestionStatistic: IDragAndDropQuestionStatistic): Observable<EntityResponseType> {
        return this.http.post<IDragAndDropQuestionStatistic>(this.resourceUrl, dragAndDropQuestionStatistic, { observe: 'response' });
    }

    update(dragAndDropQuestionStatistic: IDragAndDropQuestionStatistic): Observable<EntityResponseType> {
        return this.http.put<IDragAndDropQuestionStatistic>(this.resourceUrl, dragAndDropQuestionStatistic, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IDragAndDropQuestionStatistic>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IDragAndDropQuestionStatistic[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
