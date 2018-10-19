import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { IQuizSubmission } from 'app/shared/model/quiz-submission.model';

type EntityResponseType = HttpResponse<IQuizSubmission>;
type EntityArrayResponseType = HttpResponse<IQuizSubmission[]>;

@Injectable({ providedIn: 'root' })
export class QuizSubmissionService {
    public resourceUrl = SERVER_API_URL + 'api/quiz-submissions';

    constructor(private http: HttpClient) {}

    create(quizSubmission: IQuizSubmission): Observable<EntityResponseType> {
        return this.http.post<IQuizSubmission>(this.resourceUrl, quizSubmission, { observe: 'response' });
    }

    update(quizSubmission: IQuizSubmission): Observable<EntityResponseType> {
        return this.http.put<IQuizSubmission>(this.resourceUrl, quizSubmission, { observe: 'response' });
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<IQuizSubmission>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<IQuizSubmission[]>(this.resourceUrl, { params: options, observe: 'response' });
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }
}
