import { Component, input } from '@angular/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';

@Component({
    selector: 'jhi-quiz-exercise-group-cell',
    templateUrl: './quiz-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
})
export class QuizExerciseGroupCellComponent {
    exerciseType = ExerciseType;
    exercise = input.required<QuizExercise>();
}
