import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IPointCounter } from 'app/shared/model/point-counter.model';

type EntityResponseType = HttpResponse<IPointCounter>;
type EntityArrayResponseType = HttpResponse<IPointCounter[]>;

@Injectable({ providedIn: 'root' })
export class PointCounterService {
    public resourceUrl = SERVER_API_URL + 'api/point-counters';

    constructor(private http: HttpClient) {}

    create(pointCounter: IPointCounter): Observable<EntityResponseType> {
        return this.http.post<IPointCounter>(this.resourceUrl, pointCounter, { observe: 'response' });
    }

    update(pointCounter: IPointCounter): Observable<EntityResponseType> {
        return this.http.put<IPointCounter>(this.resourceUrl, pointCounter, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IPointCounter>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IPointCounter[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
