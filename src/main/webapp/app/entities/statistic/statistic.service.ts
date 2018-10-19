import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IStatistic } from 'app/shared/model/statistic.model';

type EntityResponseType = HttpResponse<IStatistic>;
type EntityArrayResponseType = HttpResponse<IStatistic[]>;

@Injectable({ providedIn: 'root' })
export class StatisticService {
    public resourceUrl = SERVER_API_URL + 'api/statistics';

    constructor(private http: HttpClient) {}

    create(statistic: IStatistic): Observable<EntityResponseType> {
        return this.http.post<IStatistic>(this.resourceUrl, statistic, { observe: 'response' });
    }

    update(statistic: IStatistic): Observable<EntityResponseType> {
        return this.http.put<IStatistic>(this.resourceUrl, statistic, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IStatistic>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IStatistic[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
