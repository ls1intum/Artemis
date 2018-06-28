import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { DragAndDropAssignment } from './drag-and-drop-assignment.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<DragAndDropAssignment>;

@Injectable()
export class DragAndDropAssignmentService {

    private resourceUrl =  SERVER_API_URL + 'api/drag-and-drop-assignments';

    constructor(private http: HttpClient) { }

    create(dragAndDropAssignment: DragAndDropAssignment): Observable<EntityResponseType> {
        const copy = this.convert(dragAndDropAssignment);
        return this.http.post<DragAndDropAssignment>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(dragAndDropAssignment: DragAndDropAssignment): Observable<EntityResponseType> {
        const copy = this.convert(dragAndDropAssignment);
        return this.http.put<DragAndDropAssignment>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<DragAndDropAssignment>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<DragAndDropAssignment[]>> {
        const options = createRequestOption(req);
        return this.http.get<DragAndDropAssignment[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<DragAndDropAssignment[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: DragAndDropAssignment = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<DragAndDropAssignment[]>): HttpResponse<DragAndDropAssignment[]> {
        const jsonResponse: DragAndDropAssignment[] = res.body;
        const body: DragAndDropAssignment[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to DragAndDropAssignment.
     */
    private convertItemFromServer(dragAndDropAssignment: DragAndDropAssignment): DragAndDropAssignment {
        const copy: DragAndDropAssignment = Object.assign({}, dragAndDropAssignment);
        return copy;
    }

    /**
     * Convert a DragAndDropAssignment to a JSON which can be sent to the server.
     */
    private convert(dragAndDropAssignment: DragAndDropAssignment): DragAndDropAssignment {
        const copy: DragAndDropAssignment = Object.assign({}, dragAndDropAssignment);
        return copy;
    }
}
