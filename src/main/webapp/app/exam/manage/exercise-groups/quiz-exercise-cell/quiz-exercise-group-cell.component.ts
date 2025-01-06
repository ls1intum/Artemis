import { Component, Input } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Component({
    selector: 'jhi-quiz-exercise-group-cell',
    templateUrl: './quiz-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
})
export class QuizExerciseGroupCellComponent {
    exerciseType = ExerciseType;
    quizExercise: QuizExercise;

    @Input()
    set exercise(exercise: Exercise) {
        this.quizExercise = exercise as QuizExercise;
    }
}
