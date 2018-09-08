import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { ISubmission } from 'app/shared/model/submission.model';

type EntityResponseType = HttpResponse<ISubmission>;
type EntityArrayResponseType = HttpResponse<ISubmission[]>;

@Injectable({ providedIn: 'root' })
export class SubmissionService {
    private resourceUrl = SERVER_API_URL + 'api/submissions';

    constructor(private http: HttpClient) {}

    create(submission: ISubmission): Observable<EntityResponseType> {
        return this.http.post<ISubmission>(this.resourceUrl, submission, { observe: 'response' });
    }

    update(submission: ISubmission): Observable<EntityResponseType> {
        return this.http.put<ISubmission>(this.resourceUrl, submission, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<ISubmission>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<ISubmission[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
