import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { ILtiUserId } from 'app/shared/model/lti-user-id.model';

type EntityResponseType = HttpResponse<ILtiUserId>;
type EntityArrayResponseType = HttpResponse<ILtiUserId[]>;

@Injectable({ providedIn: 'root' })
export class LtiUserIdService {
    public resourceUrl = SERVER_API_URL + 'api/lti-user-ids';

    constructor(private http: HttpClient) {}

    create(ltiUserId: ILtiUserId): Observable<EntityResponseType> {
        return this.http.post<ILtiUserId>(this.resourceUrl, ltiUserId, { observe: 'response' });
    }

    update(ltiUserId: ILtiUserId): Observable<EntityResponseType> {
        return this.http.put<ILtiUserId>(this.resourceUrl, ltiUserId, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<ILtiUserId>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<ILtiUserId[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
