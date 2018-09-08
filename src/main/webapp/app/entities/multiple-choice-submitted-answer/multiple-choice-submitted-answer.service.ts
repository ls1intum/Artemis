import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IMultipleChoiceSubmittedAnswer } from 'app/shared/model/multiple-choice-submitted-answer.model';

type EntityResponseType = HttpResponse<IMultipleChoiceSubmittedAnswer>;
type EntityArrayResponseType = HttpResponse<IMultipleChoiceSubmittedAnswer[]>;

@Injectable({ providedIn: 'root' })
export class MultipleChoiceSubmittedAnswerService {
    private resourceUrl = SERVER_API_URL + 'api/multiple-choice-submitted-answers';

    constructor(private http: HttpClient) {}

    create(multipleChoiceSubmittedAnswer: IMultipleChoiceSubmittedAnswer): Observable<EntityResponseType> {
        return this.http.post<IMultipleChoiceSubmittedAnswer>(this.resourceUrl, multipleChoiceSubmittedAnswer, { observe: 'response' });
    }

    update(multipleChoiceSubmittedAnswer: IMultipleChoiceSubmittedAnswer): Observable<EntityResponseType> {
        return this.http.put<IMultipleChoiceSubmittedAnswer>(this.resourceUrl, multipleChoiceSubmittedAnswer, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IMultipleChoiceSubmittedAnswer>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IMultipleChoiceSubmittedAnswer[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
