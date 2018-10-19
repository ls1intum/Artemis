import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { ITextSubmission } from 'app/shared/model/text-submission.model';

type EntityResponseType = HttpResponse<ITextSubmission>;
type EntityArrayResponseType = HttpResponse<ITextSubmission[]>;

@Injectable({ providedIn: 'root' })
export class TextSubmissionService {
    public resourceUrl = SERVER_API_URL + 'api/text-submissions';

    constructor(private http: HttpClient) {}

    create(textSubmission: ITextSubmission): Observable<EntityResponseType> {
        return this.http.post<ITextSubmission>(this.resourceUrl, textSubmission, { observe: 'response' });
    }

    update(textSubmission: ITextSubmission): Observable<EntityResponseType> {
        return this.http.put<ITextSubmission>(this.resourceUrl, textSubmission, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<ITextSubmission>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<ITextSubmission[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
