import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IDragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';

type EntityResponseType = HttpResponse<IDragAndDropAssignment>;
type EntityArrayResponseType = HttpResponse<IDragAndDropAssignment[]>;

@Injectable({ providedIn: 'root' })
export class DragAndDropAssignmentService {
    private resourceUrl = SERVER_API_URL + 'api/drag-and-drop-assignments';

    constructor(private http: HttpClient) {}

    create(dragAndDropAssignment: IDragAndDropAssignment): Observable<EntityResponseType> {
        return this.http.post<IDragAndDropAssignment>(this.resourceUrl, dragAndDropAssignment, { observe: 'response' });
    }

    update(dragAndDropAssignment: IDragAndDropAssignment): Observable<EntityResponseType> {
        return this.http.put<IDragAndDropAssignment>(this.resourceUrl, dragAndDropAssignment, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IDragAndDropAssignment>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IDragAndDropAssignment[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
