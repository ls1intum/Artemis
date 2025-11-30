import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { map } from 'rxjs/operators';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { createQuizSubmissionFromStudentDTO } from 'app/quiz/shared/entities/quiz-submission-from-student-dto.model';

export type EntityResponseType = HttpResponse<QuizSubmission>;
export type ResultResponseType = HttpResponse<Result>;

@Injectable({ providedIn: 'root' })
export class QuizParticipationService {
    private http = inject(HttpClient);
    private submissionService = inject(SubmissionService);

    submitForPractice(quizSubmission: QuizSubmission, exerciseId: number): Observable<ResultResponseType> {
        const dto = createQuizSubmissionFromStudentDTO(quizSubmission);
        return this.http
            .post<Result>(`api/quiz/exercises/${exerciseId}/submissions/practice`, dto, { observe: 'response' })
            .pipe(map((res: ResultResponseType) => this.submissionService.convertResponse(res)));
    }

    submitForPreview(quizSubmission: QuizSubmission, exerciseId: number): Observable<ResultResponseType> {
        const dto = createQuizSubmissionFromStudentDTO(quizSubmission);
        return this.http
            .post<Result>(`api/quiz/exercises/${exerciseId}/submissions/preview`, dto, { observe: 'response' })
            .pipe(map((res: ResultResponseType) => this.submissionService.convertResponse(res)));
    }

    saveOrSubmitForLiveMode(quizSubmission: QuizSubmission, exerciseId: number, submit: boolean): Observable<EntityResponseType> {
        const copy = this.submissionService.convert(quizSubmission);
        // there should be no results yet
        copy.results = undefined;
        return this.http
            .post<QuizSubmission>(`api/quiz/exercises/${exerciseId}/submissions/live`, copy, { observe: 'response', params: { submit } })
            .pipe(map((res: EntityResponseType) => this.submissionService.convertResponse(res)));
    }
}
