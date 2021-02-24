import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { Result } from 'app/entities/result.model';

export type EntityResponseType = HttpResponse<QuizSubmission>;
export type ResultResponseType = HttpResponse<Result>;

@Injectable({ providedIn: 'root' })
export class QuizParticipationService {
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

    submitForLiveMode(quizSubmission: QuizSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.convert(quizSubmission);
        return this.http
            .post<QuizSubmission>(`api/exercises/${exerciseId}/submissions/live`, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    private convertResponse<T>(res: HttpResponse<T>): HttpResponse<T> {
        const body: T = this.convertItemFromServer(res.body!);
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
