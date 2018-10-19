import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IDragAndDropMapping } from 'app/shared/model/drag-and-drop-mapping.model';

type EntityResponseType = HttpResponse<IDragAndDropMapping>;
type EntityArrayResponseType = HttpResponse<IDragAndDropMapping[]>;

@Injectable({ providedIn: 'root' })
export class DragAndDropMappingService {
    public resourceUrl = SERVER_API_URL + 'api/drag-and-drop-mappings';

    constructor(private http: HttpClient) {}

    create(dragAndDropMapping: IDragAndDropMapping): Observable<EntityResponseType> {
        return this.http.post<IDragAndDropMapping>(this.resourceUrl, dragAndDropMapping, { observe: 'response' });
    }

    update(dragAndDropMapping: IDragAndDropMapping): Observable<EntityResponseType> {
        return this.http.put<IDragAndDropMapping>(this.resourceUrl, dragAndDropMapping, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IDragAndDropMapping>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IDragAndDropMapping[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
