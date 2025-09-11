import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuizTrainingAnswer } from 'app/quiz/overview/course-training-quiz/quiz-training-answer.model';
import { SubmittedAnswerAfterEvaluation } from 'app/quiz/overview/course-training-quiz/SubmittedAnswerAfterEvaluation';
import { QuizQuestionTraining } from 'app/quiz/overview/course-training-quiz/quiz-question-training.model';

@Injectable({
    providedIn: 'root',
})
export class CourseTrainingQuizService {
    private http = inject(HttpClient);

    /**
     * Retrieves a set of quiz questions for a given course by course ID from the server and returns them as an Observable.
     * @param courseId
     */
    getQuizQuestions(courseId: number): Observable<QuizQuestionTraining[]> {
        return this.http.get<QuizQuestionTraining[]>(`api/quiz/courses/${courseId}/training-questions`);
    }

    submitForTraining(answer: QuizTrainingAnswer, questionId: number, courseId: number): Observable<HttpResponse<SubmittedAnswerAfterEvaluation>> {
        return this.http.post<SubmittedAnswerAfterEvaluation>(`api/quiz/courses/${courseId}/training-questions/${questionId}/submit`, answer, { observe: 'response' });
    }
}
