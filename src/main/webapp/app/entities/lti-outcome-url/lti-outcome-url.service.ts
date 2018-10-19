import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { ILtiOutcomeUrl } from 'app/shared/model/lti-outcome-url.model';

type EntityResponseType = HttpResponse<ILtiOutcomeUrl>;
type EntityArrayResponseType = HttpResponse<ILtiOutcomeUrl[]>;

@Injectable({ providedIn: 'root' })
export class LtiOutcomeUrlService {
    public resourceUrl = SERVER_API_URL + 'api/lti-outcome-urls';

    constructor(private http: HttpClient) {}

    create(ltiOutcomeUrl: ILtiOutcomeUrl): Observable<EntityResponseType> {
        return this.http.post<ILtiOutcomeUrl>(this.resourceUrl, ltiOutcomeUrl, { observe: 'response' });
    }

    update(ltiOutcomeUrl: ILtiOutcomeUrl): Observable<EntityResponseType> {
        return this.http.put<ILtiOutcomeUrl>(this.resourceUrl, ltiOutcomeUrl, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<ILtiOutcomeUrl>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<ILtiOutcomeUrl[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
