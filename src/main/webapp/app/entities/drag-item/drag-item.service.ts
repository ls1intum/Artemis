import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { DragItem } from './drag-item.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<DragItem>;

@Injectable()
export class DragItemService {

    private resourceUrl =  SERVER_API_URL + 'api/drag-items';

    constructor(private http: HttpClient) { }

    create(dragItem: DragItem): Observable<EntityResponseType> {
        const copy = this.convert(dragItem);
        return this.http.post<DragItem>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(dragItem: DragItem): Observable<EntityResponseType> {
        const copy = this.convert(dragItem);
        return this.http.put<DragItem>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<DragItem>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<DragItem[]>> {
        const options = createRequestOption(req);
        return this.http.get<DragItem[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<DragItem[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: DragItem = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<DragItem[]>): HttpResponse<DragItem[]> {
        const jsonResponse: DragItem[] = res.body;
        const body: DragItem[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to DragItem.
     */
    private convertItemFromServer(dragItem: DragItem): DragItem {
        const copy: DragItem = Object.assign({}, dragItem);
        return copy;
    }

    /**
     * Convert a DragItem to a JSON which can be sent to the server.
     */
    private convert(dragItem: DragItem): DragItem {
        const copy: DragItem = Object.assign({}, dragItem);
        return copy;
    }
}
