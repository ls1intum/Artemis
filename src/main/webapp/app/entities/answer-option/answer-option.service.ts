import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IAnswerOption } from 'app/shared/model/answer-option.model';

type EntityResponseType = HttpResponse<IAnswerOption>;
type EntityArrayResponseType = HttpResponse<IAnswerOption[]>;

@Injectable({ providedIn: 'root' })
export class AnswerOptionService {
    private resourceUrl = SERVER_API_URL + 'api/answer-options';

    constructor(private http: HttpClient) {}

    create(answerOption: IAnswerOption): Observable<EntityResponseType> {
        return this.http.post<IAnswerOption>(this.resourceUrl, answerOption, { observe: 'response' });
    }

    update(answerOption: IAnswerOption): Observable<EntityResponseType> {
        return this.http.put<IAnswerOption>(this.resourceUrl, answerOption, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IAnswerOption>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IAnswerOption[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
