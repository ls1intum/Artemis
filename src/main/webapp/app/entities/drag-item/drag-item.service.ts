import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IDragItem } from 'app/shared/model/drag-item.model';

type EntityResponseType = HttpResponse<IDragItem>;
type EntityArrayResponseType = HttpResponse<IDragItem[]>;

@Injectable({ providedIn: 'root' })
export class DragItemService {
    public resourceUrl = SERVER_API_URL + 'api/drag-items';

    constructor(private http: HttpClient) {}

    create(dragItem: IDragItem): Observable<EntityResponseType> {
        return this.http.post<IDragItem>(this.resourceUrl, dragItem, { observe: 'response' });
    }

    update(dragItem: IDragItem): Observable<EntityResponseType> {
        return this.http.put<IDragItem>(this.resourceUrl, dragItem, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IDragItem>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IDragItem[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
