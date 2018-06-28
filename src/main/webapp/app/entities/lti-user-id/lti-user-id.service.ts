import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { LtiUserId } from './lti-user-id.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<LtiUserId>;

@Injectable()
export class LtiUserIdService {

    private resourceUrl =  SERVER_API_URL + 'api/lti-user-ids';

    constructor(private http: HttpClient) { }

    create(ltiUserId: LtiUserId): Observable<EntityResponseType> {
        const copy = this.convert(ltiUserId);
        return this.http.post<LtiUserId>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(ltiUserId: LtiUserId): Observable<EntityResponseType> {
        const copy = this.convert(ltiUserId);
        return this.http.put<LtiUserId>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<LtiUserId>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<LtiUserId[]>> {
        const options = createRequestOption(req);
        return this.http.get<LtiUserId[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<LtiUserId[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: LtiUserId = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<LtiUserId[]>): HttpResponse<LtiUserId[]> {
        const jsonResponse: LtiUserId[] = res.body;
        const body: LtiUserId[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to LtiUserId.
     */
    private convertItemFromServer(ltiUserId: LtiUserId): LtiUserId {
        const copy: LtiUserId = Object.assign({}, ltiUserId);
        return copy;
    }

    /**
     * Convert a LtiUserId to a JSON which can be sent to the server.
     */
    private convert(ltiUserId: LtiUserId): LtiUserId {
        const copy: LtiUserId = Object.assign({}, ltiUserId);
        return copy;
    }
}
