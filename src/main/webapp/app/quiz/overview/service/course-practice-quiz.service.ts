import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';

@Injectable({
    providedIn: 'root',
})
export class CoursePracticeQuizService {
    private http = inject(HttpClient);

    /**
     * Retrieves a set of quiz questions for a given course by course ID from the server and returns them as an Observable.
     * @param courseId
     */
    getQuizQuestions(courseId: number): Observable<QuizQuestion[]> {
        return this.http.get<QuizQuestion[]>(`api/quiz/courses/${courseId}/practice/quiz`);
    }
}
