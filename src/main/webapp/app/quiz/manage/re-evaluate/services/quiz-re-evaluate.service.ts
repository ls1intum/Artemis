import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { objectToJsonBlob } from 'app/shared/util/blob-util';
import { QuizExerciseReEvaluateDTO, convertQuizExerciseToReEvaluateDTO } from 'app/quiz/shared/entities/quiz-exercise-reevaluation/quiz-exercise-reevaluate-dto.model';

@Injectable({ providedIn: 'root' })
export class QuizReEvaluateService {
    private http = inject(HttpClient);

    private resourceUrl = 'api/quiz/quiz-exercises/';

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
    private convert(quizExercise: QuizExercise): QuizExerciseReEvaluateDTO {
        const copy: QuizExercise = Object.assign({}, quizExercise);
        copy.categories = ExerciseService.stringifyExerciseCategories(copy);
        return convertQuizExerciseToReEvaluateDTO(copy);
    }
}
