import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IMultipleChoiceQuestion } from 'app/shared/model/multiple-choice-question.model';

type EntityResponseType = HttpResponse<IMultipleChoiceQuestion>;
type EntityArrayResponseType = HttpResponse<IMultipleChoiceQuestion[]>;

@Injectable({ providedIn: 'root' })
export class MultipleChoiceQuestionService {
    public resourceUrl = SERVER_API_URL + 'api/multiple-choice-questions';

    constructor(private http: HttpClient) {}

    create(multipleChoiceQuestion: IMultipleChoiceQuestion): Observable<EntityResponseType> {
        return this.http.post<IMultipleChoiceQuestion>(this.resourceUrl, multipleChoiceQuestion, { observe: 'response' });
    }

    update(multipleChoiceQuestion: IMultipleChoiceQuestion): Observable<EntityResponseType> {
        return this.http.put<IMultipleChoiceQuestion>(this.resourceUrl, multipleChoiceQuestion, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IMultipleChoiceQuestion>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IMultipleChoiceQuestion[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
