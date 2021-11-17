import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

@Injectable({ providedIn: 'root' })
export class QuizReEvaluateService {
    private resourceUrl = SERVER_API_URL + 'api/quiz-exercises/';

    constructor(private http: HttpClient, private exerciseService: ExerciseService) {}

    update(quizExercise: QuizExercise) {
        const copy = this.convert(quizExercise);
        return this.http.put<QuizExercise>(this.resourceUrl + quizExercise.id + '/re-evaluate', copy, { observe: 'response' });
    }

    /**
     * Copy the QuizExercise object
     */
    private convert(quizExercise: QuizExercise): QuizExercise {
        const copy: QuizExercise = Object.assign({}, quizExercise);
        copy.categories = this.exerciseService.stringifyExerciseCategories(copy);
        return copy;
    }
}
