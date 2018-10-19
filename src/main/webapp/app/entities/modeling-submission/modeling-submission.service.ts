import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IModelingSubmission } from 'app/shared/model/modeling-submission.model';

type EntityResponseType = HttpResponse<IModelingSubmission>;
type EntityArrayResponseType = HttpResponse<IModelingSubmission[]>;

@Injectable({ providedIn: 'root' })
export class ModelingSubmissionService {
    public resourceUrl = SERVER_API_URL + 'api/modeling-submissions';

    constructor(private http: HttpClient) {}

    create(modelingSubmission: IModelingSubmission): Observable<EntityResponseType> {
        return this.http.post<IModelingSubmission>(this.resourceUrl, modelingSubmission, { observe: 'response' });
    }

    update(modelingSubmission: IModelingSubmission): Observable<EntityResponseType> {
        return this.http.put<IModelingSubmission>(this.resourceUrl, modelingSubmission, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IModelingSubmission>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IModelingSubmission[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
