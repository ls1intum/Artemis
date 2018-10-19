import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IDragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';

type EntityResponseType = HttpResponse<IDragAndDropSubmittedAnswer>;
type EntityArrayResponseType = HttpResponse<IDragAndDropSubmittedAnswer[]>;

@Injectable({ providedIn: 'root' })
export class DragAndDropSubmittedAnswerService {
    public resourceUrl = SERVER_API_URL + 'api/drag-and-drop-submitted-answers';

    constructor(private http: HttpClient) {}

    create(dragAndDropSubmittedAnswer: IDragAndDropSubmittedAnswer): Observable<EntityResponseType> {
        return this.http.post<IDragAndDropSubmittedAnswer>(this.resourceUrl, dragAndDropSubmittedAnswer, { observe: 'response' });
    }

    update(dragAndDropSubmittedAnswer: IDragAndDropSubmittedAnswer): Observable<EntityResponseType> {
        return this.http.put<IDragAndDropSubmittedAnswer>(this.resourceUrl, dragAndDropSubmittedAnswer, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IDragAndDropSubmittedAnswer>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IDragAndDropSubmittedAnswer[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
