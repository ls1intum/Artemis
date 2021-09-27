import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Injectable({ providedIn: 'root' })
export class QuizReEvaluateService {
    private resourceUrl = SERVER_API_URL + 'api/quiz-exercises/';

    constructor(private http: HttpClient) {}

    update(quizExercise: QuizExercise) {
        const copy = this.convert(quizExercise);
        return this.http.put<QuizExercise>(this.resourceUrl + quizExercise.id + '/re-evaluate', copy, { observe: 'response' });
    }

    /**
     * Convert a QuizExercise to a JSON which can be sent to the server.
     */
    private convert(quizExercise: QuizExercise): QuizExercise {
        const copy: QuizExercise = Object.assign({}, quizExercise);
        return copy;
    }
}
