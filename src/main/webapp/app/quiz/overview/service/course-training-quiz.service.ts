import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuizTrainingAnswer } from 'app/quiz/overview/course-training-quiz/quiz-training-answer.model';
import { SubmittedAnswerAfterEvaluation } from 'app/quiz/overview/course-training-quiz/SubmittedAnswerAfterEvaluation';
import { QuizQuestionTraining } from 'app/quiz/overview/course-training-quiz/quiz-question-training.model';
import { createRequestOption } from 'app/shared/util/request.util';

@Injectable({
    providedIn: 'root',
})
export class CourseTrainingQuizService {
    private http = inject(HttpClient);

    /**
     * Retrieves a set of quiz questions for a given course by course ID from the server and returns them as an Observable.
     * @param courseId The course ID for which to retrieve quiz questions.
     * @param req Pagination options
     */
    getQuizQuestions(courseId: number, req?: any): Observable<HttpResponse<QuizQuestionTraining[]>> {
        const options = createRequestOption(req);
        return this.http.get<QuizQuestionTraining[]>(`api/quiz/courses/${courseId}/training-questions`, {
            params: options,
            observe: 'response',
        });
    }

    /**
     * Helper function to create request options for pagination.
     * @param courseId The course ID for which to retrieve quiz questions
     * @param page the page number to retrieve
     * @param size the number of items per page
     */
    getQuizQuestionsPage(courseId: number, page: number, size: number): Observable<HttpResponse<QuizQuestionTraining[]>> {
        const req = {
            page,
            size,
        };
        return this.getQuizQuestions(courseId, req);
    }

    submitForTraining(answer: QuizTrainingAnswer, questionId: number, courseId: number): Observable<HttpResponse<SubmittedAnswerAfterEvaluation>> {
        return this.http.post<SubmittedAnswerAfterEvaluation>(`api/quiz/courses/${courseId}/training-questions/${questionId}/submit`, answer, { observe: 'response' });
    }
}
