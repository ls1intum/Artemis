import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { JhiDateUtils } from 'ng-jhipster';

import { Participation } from './participation.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<Participation>;

@Injectable()
export class ParticipationService {

    private resourceUrl =  SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) { }

    create(participation: Participation): Observable<EntityResponseType> {
        const copy = this.convert(participation);
        return this.http.post<Participation>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(participation: Participation): Observable<EntityResponseType> {
        const copy = this.convert(participation);
        return this.http.put<Participation>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<Participation>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<Participation[]>> {
        const options = createRequestOption(req);
        return this.http.get<Participation[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<Participation[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Participation = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<Participation[]>): HttpResponse<Participation[]> {
        const jsonResponse: Participation[] = res.body;
        const body: Participation[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Participation.
     */
    private convertItemFromServer(participation: Participation): Participation {
        const copy: Participation = Object.assign({}, participation);
        copy.initializationDate = this.dateUtils
            .convertDateTimeFromServer(participation.initializationDate);
        return copy;
    }

    /**
     * Convert a Participation to a JSON which can be sent to the server.
     */
    private convert(participation: Participation): Participation {
        const copy: Participation = Object.assign({}, participation);

        copy.initializationDate = this.dateUtils.toDate(participation.initializationDate);
        return copy;
    }
}
