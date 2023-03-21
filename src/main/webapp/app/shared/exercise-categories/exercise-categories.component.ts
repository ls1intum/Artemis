import { Component, Input } from '@angular/core';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Component({
    selector: 'jhi-exercise-categories',
    templateUrl: './exercise-categories.component.html',
})
export class ExerciseCategoriesComponent {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly dayjs = dayjs;

    @Input() exercise: Exercise;

    @Input() shouldShowNotReleasedTag = false;
    @Input() shouldShowQuizLiveTag = false;
    @Input() shouldShowDifficultyTag = false;
    @Input() shouldShowDifficultyTagIfNoLevel = false;
    @Input() shouldShowIncludedInScoreBadge = false;

    asQuizExercise(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }
}
