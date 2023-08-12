import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { Result } from 'app/entities/result.model';
import { map } from 'rxjs/operators';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';

export type EntityResponseType = HttpResponse<QuizSubmission>;
export type ResultResponseType = HttpResponse<Result>;

@Injectable({ providedIn: 'root' })
export class QuizParticipationService {
    constructor(
        private http: HttpClient,
        private submissionService: SubmissionService,
    ) {}

    submitForPractice(quizSubmission: QuizSubmission, exerciseId: number): Observable<ResultResponseType> {
        const copy = this.submissionService.convert(quizSubmission);
        return this.http
            .post<Result>(`api/exercises/${exerciseId}/submissions/practice`, copy, { observe: 'response' })
            .pipe(map((res: ResultResponseType) => this.submissionService.convertResponse(res)));
    }

    submitForPreview(quizSubmission: QuizSubmission, exerciseId: number): Observable<ResultResponseType> {
        const copy = this.submissionService.convert(quizSubmission);
        return this.http
            .post<Result>(`api/exercises/${exerciseId}/submissions/preview`, copy, { observe: 'response' })
            .pipe(map((res: ResultResponseType) => this.submissionService.convertResponse(res)));
    }

    submitForLiveMode(quizSubmission: QuizSubmission, exerciseId: number): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(quizSubmission);
        return this.http
            .post<QuizSubmission>(`api/exercises/${exerciseId}/submissions/live`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }
}
