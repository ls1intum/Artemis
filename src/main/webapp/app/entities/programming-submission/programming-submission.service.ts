import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IProgrammingSubmission } from 'app/shared/model/programming-submission.model';

type EntityResponseType = HttpResponse<IProgrammingSubmission>;
type EntityArrayResponseType = HttpResponse<IProgrammingSubmission[]>;

@Injectable({ providedIn: 'root' })
export class ProgrammingSubmissionService {
    public resourceUrl = SERVER_API_URL + 'api/programming-submissions';

    constructor(private http: HttpClient) {}

    create(programmingSubmission: IProgrammingSubmission): Observable<EntityResponseType> {
        return this.http.post<IProgrammingSubmission>(this.resourceUrl, programmingSubmission, { observe: 'response' });
    }

    update(programmingSubmission: IProgrammingSubmission): Observable<EntityResponseType> {
        return this.http.put<IProgrammingSubmission>(this.resourceUrl, programmingSubmission, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IProgrammingSubmission>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IProgrammingSubmission[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
