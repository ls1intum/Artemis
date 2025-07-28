import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ResultResponseType } from 'app/quiz/overview/service/quiz-participation.service';
import { SubmissionService } from 'app/exercise/submission/submission.service';

@Injectable({
    providedIn: 'root',
})
export class CourseTrainingQuizService {
    private http = inject(HttpClient);
    private submissionService = inject(SubmissionService);

    /**
     * Retrieves a set of quiz questions for a given course by course ID from the server and returns them as an Observable.
     * @param courseId
     */
    getQuizQuestions(courseId: number): Observable<QuizQuestion[]> {
        return this.http.get<QuizQuestion[]>(`api/quiz/courses/${courseId}/practice/quiz`);
    }

    submitForTraining(quizSubmission: QuizSubmission, exerciseId: number): Observable<ResultResponseType> {
        const copy = this.submissionService.convert(quizSubmission);
        return this.http
            .post<Result>(`api/quiz/exercises/${exerciseId}/submissions/training`, copy, { observe: 'response' })
            .pipe(map((res: ResultResponseType) => this.submissionService.convertResponse(res)));
    }
}
