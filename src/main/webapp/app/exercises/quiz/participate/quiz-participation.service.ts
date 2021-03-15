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
        const copy = QuizParticipationService.convert(quizSubmission);
        return this.http
            .post<Result>(`api/exercises/${exerciseId}/submissions/practice`, copy, { observe: 'response' })
            .map((res: ResultResponseType) => QuizParticipationService.convertResponse(res));
    }

    submitForPreview(quizSubmission: QuizSubmission, exerciseId: number): Observable<ResultResponseType> {
        const copy = QuizParticipationService.convert(quizSubmission);
        return this.http
            .post<Result>(`api/exercises/${exerciseId}/submissions/preview`, copy, { observe: 'response' })
            .map((res: ResultResponseType) => QuizParticipationService.convertResponse(res));
    }

    submitForLiveMode(quizSubmission: QuizSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = QuizParticipationService.convert(quizSubmission);
        return this.http
            .post<QuizSubmission>(`api/exercises/${exerciseId}/submissions/live`, copy, { observe: 'response' })
            .map((res: EntityResponseType) => QuizParticipationService.convertResponse(res));
    }

    private static convertResponse<T>(res: HttpResponse<T>): HttpResponse<T> {
        const body: T = QuizParticipationService.convertItemFromServer(res.body!);
        return res.clone({ body });
    }

    /**
     * Convert a returned JSON object to QuizSubmission.
     */
    private static convertItemFromServer<T>(object: T): T {
        return Object.assign({}, object);
    }

    /**
     * Convert a QuizSubmission to a JSON which can be sent to the server.
     */
    private static convert(quizSubmission: QuizSubmission): QuizSubmission {
        return Object.assign({}, quizSubmission);
    }
}
