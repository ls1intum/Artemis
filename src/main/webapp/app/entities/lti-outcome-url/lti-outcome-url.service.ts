import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { LtiOutcomeUrl } from './lti-outcome-url.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<LtiOutcomeUrl>;

@Injectable()
export class LtiOutcomeUrlService {

    private resourceUrl =  SERVER_API_URL + 'api/lti-outcome-urls';

    constructor(private http: HttpClient) { }

    create(ltiOutcomeUrl: LtiOutcomeUrl): Observable<EntityResponseType> {
        const copy = this.convert(ltiOutcomeUrl);
        return this.http.post<LtiOutcomeUrl>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(ltiOutcomeUrl: LtiOutcomeUrl): Observable<EntityResponseType> {
        const copy = this.convert(ltiOutcomeUrl);
        return this.http.put<LtiOutcomeUrl>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<LtiOutcomeUrl>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<LtiOutcomeUrl[]>> {
        const options = createRequestOption(req);
        return this.http.get<LtiOutcomeUrl[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<LtiOutcomeUrl[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: LtiOutcomeUrl = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<LtiOutcomeUrl[]>): HttpResponse<LtiOutcomeUrl[]> {
        const jsonResponse: LtiOutcomeUrl[] = res.body;
        const body: LtiOutcomeUrl[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to LtiOutcomeUrl.
     */
    private convertItemFromServer(ltiOutcomeUrl: LtiOutcomeUrl): LtiOutcomeUrl {
        const copy: LtiOutcomeUrl = Object.assign({}, ltiOutcomeUrl);
        return copy;
    }

    /**
     * Convert a LtiOutcomeUrl to a JSON which can be sent to the server.
     */
    private convert(ltiOutcomeUrl: LtiOutcomeUrl): LtiOutcomeUrl {
        const copy: LtiOutcomeUrl = Object.assign({}, ltiOutcomeUrl);
        return copy;
    }
}
