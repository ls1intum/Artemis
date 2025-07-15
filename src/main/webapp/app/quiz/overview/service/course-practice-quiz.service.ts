import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { map } from 'rxjs/operators';

@Injectable({
    providedIn: 'root',
})
export class CoursePracticeQuizService {
    private http = inject(HttpClient);

    questions: Observable<QuizQuestion[]>;

    /**
     * Retrieves a set of quiz questions for a given course by course ID from the server and returns them as an Observable.
     * @param courseId
     */
    getQuizQuestions(courseId: number): Observable<QuizQuestion[]> {
        this.questions = this.http.get<Set<QuizQuestion>>(`api/quiz/courses/${courseId}/practice/quiz`).pipe(map((questions) => Array.from(questions)));
        return this.questions;
    }

    areQuestionsAvailable(courseId: number): Observable<boolean> {
        return this.getQuizQuestions(courseId).pipe(map((q) => q.length > 0));
    }
}
