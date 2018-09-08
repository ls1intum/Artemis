import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { ISubmittedAnswer } from 'app/shared/model/submitted-answer.model';

type EntityResponseType = HttpResponse<ISubmittedAnswer>;
type EntityArrayResponseType = HttpResponse<ISubmittedAnswer[]>;

@Injectable({ providedIn: 'root' })
export class SubmittedAnswerService {
    private resourceUrl = SERVER_API_URL + 'api/submitted-answers';

    constructor(private http: HttpClient) {}

    create(submittedAnswer: ISubmittedAnswer): Observable<EntityResponseType> {
        return this.http.post<ISubmittedAnswer>(this.resourceUrl, submittedAnswer, { observe: 'response' });
    }

    update(submittedAnswer: ISubmittedAnswer): Observable<EntityResponseType> {
        return this.http.put<ISubmittedAnswer>(this.resourceUrl, submittedAnswer, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<ISubmittedAnswer>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<ISubmittedAnswer[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
