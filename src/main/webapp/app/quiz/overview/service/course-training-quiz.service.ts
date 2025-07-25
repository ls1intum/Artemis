import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizTrainingAnswer } from 'app/quiz/overview/course-training-quiz/QuizTrainingAnswer';
import { SubmittedAnswerAfterEvaluationDTO } from 'app/quiz/overview/course-training-quiz/SubmittedAnswerAfterEvaluationDTO';

@Injectable({
    providedIn: 'root',
})
export class CourseTrainingQuizService {
    private http = inject(HttpClient);

    /**
     * Retrieves a set of quiz questions for a given course by course ID from the server and returns them as an Observable.
     * @param courseId
     */
    getQuizQuestions(courseId: number): Observable<QuizQuestion[]> {
        return this.http.get<QuizQuestion[]>(`api/quiz/courses/${courseId}/training/quiz`);
    }

    submitForTraining(answer: QuizTrainingAnswer, questionId: number, courseId: number): Observable<HttpResponse<SubmittedAnswerAfterEvaluationDTO>> {
        return this.http.post<SubmittedAnswerAfterEvaluationDTO>(`api/quiz/courses/${courseId}/training/${questionId}/quiz`, answer, { observe: 'response' });
    }
}
