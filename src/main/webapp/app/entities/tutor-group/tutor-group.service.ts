import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { TutorGroup } from 'app/entities/tutor-group';

type EntityResponseType = HttpResponse<TutorGroup>;
type EntityArrayResponseType = HttpResponse<TutorGroup[]>;

@Injectable({ providedIn: 'root' })
export class TutorGroupService {
    public resourceUrl = SERVER_API_URL + 'api/tutor-groups';

    constructor(protected http: HttpClient) {}

    create(tutorGroup: TutorGroup): Observable<EntityResponseType> {
        return this.http.post<TutorGroup>(this.resourceUrl, tutorGroup, { observe: 'response' });
    }

    update(tutorGroup: TutorGroup): Observable<EntityResponseType> {
        return this.http.put<TutorGroup>(this.resourceUrl, tutorGroup, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<TutorGroup>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<TutorGroup[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
