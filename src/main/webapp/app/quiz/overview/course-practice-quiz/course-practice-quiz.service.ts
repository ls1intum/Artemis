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

    getSetOfQuizQuestions(courseId: number): Observable<Set<QuizQuestion>> {
        return this.http.get<Set<QuizQuestion>>(`api/quiz/courses/${courseId}/quiz`);
    }

    getQuizQuestions(courseId: number): Observable<QuizQuestion[]> {
        return this.getSetOfQuizQuestions(courseId).pipe(map((questionsSet) => Array.from(questionsSet)));
    }
}
