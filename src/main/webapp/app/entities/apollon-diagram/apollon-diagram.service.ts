import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IApollonDiagram } from 'app/shared/model/apollon-diagram.model';

type EntityResponseType = HttpResponse<IApollonDiagram>;
type EntityArrayResponseType = HttpResponse<IApollonDiagram[]>;

@Injectable({ providedIn: 'root' })
export class ApollonDiagramService {
    public resourceUrl = SERVER_API_URL + 'api/apollon-diagrams';

    constructor(private http: HttpClient) {}

    create(apollonDiagram: IApollonDiagram): Observable<EntityResponseType> {
        return this.http.post<IApollonDiagram>(this.resourceUrl, apollonDiagram, { observe: 'response' });
    }

    update(apollonDiagram: IApollonDiagram): Observable<EntityResponseType> {
        return this.http.put<IApollonDiagram>(this.resourceUrl, apollonDiagram, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IApollonDiagram>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IApollonDiagram[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
