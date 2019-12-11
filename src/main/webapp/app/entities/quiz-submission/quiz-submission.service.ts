import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { QuizSubmission } from './quiz-submission.model';
import { Result } from '../../entities/result';

export type EntityResponseType = HttpResponse<QuizSubmission>;
export type ResultResponseType = HttpResponse<Result>;

@Injectable({ providedIn: 'root' })
export class QuizSubmissionService {
    constructor(private http: HttpClient) {}

    submitForPractice(quizSubmission: QuizSubmission, exerciseId: number): Observable<ResultResponseType> {
        const copy = this.convert(quizSubmission);
        return this.http
            .post<Result>(`api/exercises/${exerciseId}/submissions/practice`, copy, { observe: 'response' })
            .map((res: ResultResponseType) => this.convertResponse(res));
    }

    submitForPreview(quizSubmission: QuizSubmission, exerciseId: number): Observable<ResultResponseType> {
        const copy = this.convert(quizSubmission);
        return this.http
            .post<Result>(`api/exercises/${exerciseId}/submissions/preview`, copy, { observe: 'response' })
            .map((res: ResultResponseType) => this.convertResponse(res));
    }

    private convertResponse<T>(res: HttpResponse<T>): HttpResponse<T> {
        const body: T = this.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    private convertArrayResponse(res: HttpResponse<QuizSubmission[]>): HttpResponse<QuizSubmission[]> {
        const jsonResponse: QuizSubmission[] = res.body!;
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
