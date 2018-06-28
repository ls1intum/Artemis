import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { DropLocation } from './drop-location.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<DropLocation>;

@Injectable()
export class DropLocationService {

    private resourceUrl =  SERVER_API_URL + 'api/drop-locations';

    constructor(private http: HttpClient) { }

    create(dropLocation: DropLocation): Observable<EntityResponseType> {
        const copy = this.convert(dropLocation);
        return this.http.post<DropLocation>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(dropLocation: DropLocation): Observable<EntityResponseType> {
        const copy = this.convert(dropLocation);
        return this.http.put<DropLocation>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<DropLocation>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<DropLocation[]>> {
        const options = createRequestOption(req);
        return this.http.get<DropLocation[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<DropLocation[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: DropLocation = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<DropLocation[]>): HttpResponse<DropLocation[]> {
        const jsonResponse: DropLocation[] = res.body;
        const body: DropLocation[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to DropLocation.
     */
    private convertItemFromServer(dropLocation: DropLocation): DropLocation {
        const copy: DropLocation = Object.assign({}, dropLocation);
        return copy;
    }

    /**
     * Convert a DropLocation to a JSON which can be sent to the server.
     */
    private convert(dropLocation: DropLocation): DropLocation {
        const copy: DropLocation = Object.assign({}, dropLocation);
        return copy;
    }
}
