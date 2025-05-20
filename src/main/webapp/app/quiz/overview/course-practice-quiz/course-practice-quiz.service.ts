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

    /**
     * Retrieves a set of quiz questions for a given course by course ID from the server and returns them as an Observable.
     * @param courseId
     */
    getSetOfQuizQuestions(courseId: number): Observable<Set<QuizQuestion>> {
        return this.http.get<Set<QuizQuestion>>(`api/quiz/courses/${courseId}/quiz`);
    }

    /**
     * Converts the set of quiz questions received from the server into an array and returns it as an Observable.
     * @param courseId
     */
    getQuizQuestions(courseId: number): Observable<QuizQuestion[]> {
        return this.getSetOfQuizQuestions(courseId).pipe(map((questionsSet) => Array.from(questionsSet)));
    }
}
