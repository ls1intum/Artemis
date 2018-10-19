import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IAnswerCounter } from 'app/shared/model/answer-counter.model';

type EntityResponseType = HttpResponse<IAnswerCounter>;
type EntityArrayResponseType = HttpResponse<IAnswerCounter[]>;

@Injectable({ providedIn: 'root' })
export class AnswerCounterService {
    public resourceUrl = SERVER_API_URL + 'api/answer-counters';

    constructor(private http: HttpClient) {}

    create(answerCounter: IAnswerCounter): Observable<EntityResponseType> {
        return this.http.post<IAnswerCounter>(this.resourceUrl, answerCounter, { observe: 'response' });
    }

    update(answerCounter: IAnswerCounter): Observable<EntityResponseType> {
        return this.http.put<IAnswerCounter>(this.resourceUrl, answerCounter, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IAnswerCounter>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IAnswerCounter[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
