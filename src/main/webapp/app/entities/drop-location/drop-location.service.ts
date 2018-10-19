import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IDropLocation } from 'app/shared/model/drop-location.model';

type EntityResponseType = HttpResponse<IDropLocation>;
type EntityArrayResponseType = HttpResponse<IDropLocation[]>;

@Injectable({ providedIn: 'root' })
export class DropLocationService {
    public resourceUrl = SERVER_API_URL + 'api/drop-locations';

    constructor(private http: HttpClient) {}

    create(dropLocation: IDropLocation): Observable<EntityResponseType> {
        return this.http.post<IDropLocation>(this.resourceUrl, dropLocation, { observe: 'response' });
    }

    update(dropLocation: IDropLocation): Observable<EntityResponseType> {
        return this.http.put<IDropLocation>(this.resourceUrl, dropLocation, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IDropLocation>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IDropLocation[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
