import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { QuizSubmission } from './quiz-submission.model';
import { createRequestOption } from '../../shared';
import { Result } from '../../entities/result';

export type EntityResponseType = HttpResponse<QuizSubmission>;
export type ResultResponseType = HttpResponse<Result>;

@Injectable({ providedIn: 'root' })
export class QuizSubmissionService {
    private resourceUrl = SERVER_API_URL + 'api/quiz-submissions';

    constructor(private http: HttpClient) {}

    create(quizSubmission: QuizSubmission): Observable<EntityResponseType> {
        const copy = this.convert(quizSubmission);
        return this.http
            .post<QuizSubmission>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(quizSubmission: QuizSubmission): Observable<EntityResponseType> {
        const copy = this.convert(quizSubmission);
        return this.http
            .put<QuizSubmission>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<QuizSubmission>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<QuizSubmission[]>> {
        const options = createRequestOption(req);
        return this.http
            .get<QuizSubmission[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<QuizSubmission[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    submitForPractice(quizSubmission: QuizSubmission, courseId: number, exerciseId: number): Observable<ResultResponseType> {
        const copy = this.convert(quizSubmission);
        return this.http
            .post<Result>(`api/courses/${courseId}/exercises/${exerciseId}/submissions/practice`, copy, { observe: 'response' })
            .map((res: ResultResponseType) => this.convertResponse(res));
    }

    submitForPreview(quizSubmission: QuizSubmission, courseId: number, exerciseId: number): Observable<ResultResponseType> {
        const copy = this.convert(quizSubmission);
        return this.http
            .post<Result>(`api/courses/${courseId}/exercises/${exerciseId}/submissions/preview`, copy, { observe: 'response' })
            .map((res: ResultResponseType) => this.convertResponse(res));
    }

    private convertResponse<T>(res: HttpResponse<T>): HttpResponse<T> {
        const body: T = this.convertItemFromServer(res.body);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<QuizSubmission[]>): HttpResponse<QuizSubmission[]> {
        const jsonResponse: QuizSubmission[] = res.body;
        const body: QuizSubmission[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to QuizSubmission.
     */
    private convertItemFromServer<T>(object: T): T {
        const copy: T = Object.assign({}, object);
        return copy;
    }

    /**
     * Convert a QuizSubmission to a JSON which can be sent to the server.
     */
    private convert(quizSubmission: QuizSubmission): QuizSubmission {
        const copy: QuizSubmission = Object.assign({}, quizSubmission);
        return copy;
    }
}
