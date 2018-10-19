import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IStatisticCounter } from 'app/shared/model/statistic-counter.model';

type EntityResponseType = HttpResponse<IStatisticCounter>;
type EntityArrayResponseType = HttpResponse<IStatisticCounter[]>;

@Injectable({ providedIn: 'root' })
export class StatisticCounterService {
    public resourceUrl = SERVER_API_URL + 'api/statistic-counters';

    constructor(private http: HttpClient) {}

    create(statisticCounter: IStatisticCounter): Observable<EntityResponseType> {
        return this.http.post<IStatisticCounter>(this.resourceUrl, statisticCounter, { observe: 'response' });
    }

    update(statisticCounter: IStatisticCounter): Observable<EntityResponseType> {
        return this.http.put<IStatisticCounter>(this.resourceUrl, statisticCounter, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IStatisticCounter>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IStatisticCounter[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
