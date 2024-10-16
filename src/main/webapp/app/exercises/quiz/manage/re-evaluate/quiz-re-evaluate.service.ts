import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { objectToJsonBlob } from 'app/utils/blob-util';

@Injectable({ providedIn: 'root' })
export class QuizReEvaluateService {
    private http = inject(HttpClient);

    private resourceUrl = 'api/quiz-exercises/';

    reevaluate(quizExercise: QuizExercise, files: Map<string, Blob>) {
        const copy = this.convert(quizExercise);
        const formData = new FormData();
        formData.append('exercise', objectToJsonBlob(copy));
        files.forEach((file, fileName) => {
            formData.append('files', file, fileName);
        });
        return this.http.put<QuizExercise>(this.resourceUrl + quizExercise.id + '/re-evaluate', formData, { observe: 'response' });
    }

    /**
     * Copy the QuizExercise object
     */
    private convert(quizExercise: QuizExercise): QuizExercise {
        const copy: QuizExercise = Object.assign({}, quizExercise);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return copy;
    }
}
