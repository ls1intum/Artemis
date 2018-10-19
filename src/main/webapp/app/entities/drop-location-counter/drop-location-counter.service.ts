import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IDropLocationCounter } from 'app/shared/model/drop-location-counter.model';

type EntityResponseType = HttpResponse<IDropLocationCounter>;
type EntityArrayResponseType = HttpResponse<IDropLocationCounter[]>;

@Injectable({ providedIn: 'root' })
export class DropLocationCounterService {
    public resourceUrl = SERVER_API_URL + 'api/drop-location-counters';

    constructor(private http: HttpClient) {}

    create(dropLocationCounter: IDropLocationCounter): Observable<EntityResponseType> {
        return this.http.post<IDropLocationCounter>(this.resourceUrl, dropLocationCounter, { observe: 'response' });
    }

    update(dropLocationCounter: IDropLocationCounter): Observable<EntityResponseType> {
        return this.http.put<IDropLocationCounter>(this.resourceUrl, dropLocationCounter, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IDropLocationCounter>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IDropLocationCounter[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
