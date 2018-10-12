import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { ApollonDiagram } from './apollon-diagram.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<ApollonDiagram>;

@Injectable()
export class ApollonDiagramService {
    private resourceUrl = SERVER_API_URL + 'api/apollon-diagrams';

    constructor(private http: HttpClient) {}

    create(apollonDiagram: ApollonDiagram): Observable<EntityResponseType> {
        const copy = this.convert(apollonDiagram);
        return this.http
            .post<ApollonDiagram>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(apollonDiagram: ApollonDiagram): Observable<EntityResponseType> {
        const copy = this.convert(apollonDiagram);
        return this.http
            .put<ApollonDiagram>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<ApollonDiagram>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<ApollonDiagram[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<ApollonDiagram[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<ApollonDiagram[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: ApollonDiagram = this.convertItemFromServer(res.body);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<ApollonDiagram[]>): HttpResponse<ApollonDiagram[]> {
        const jsonResponse: ApollonDiagram[] = res.body;
        const body: ApollonDiagram[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to ApollonDiagram.
     */
    private convertItemFromServer(apollonDiagram: ApollonDiagram): ApollonDiagram {
        const copy: ApollonDiagram = Object.assign({}, apollonDiagram);
        return copy;
    }

    /**
     * Convert a ApollonDiagram to a JSON which can be sent to the server.
     */
    private convert(apollonDiagram: ApollonDiagram): ApollonDiagram {
        const copy: ApollonDiagram = Object.assign({}, apollonDiagram);
        return copy;
    }
}
