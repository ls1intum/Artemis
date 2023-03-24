import { Component, Input } from '@angular/core';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

interface ShowTagsConfig {
    notReleased?: boolean;
    quizLive?: boolean;
    difficulty?: boolean;
    difficultyIfNoLevel?: boolean;
    includedInScore?: boolean;
}

@Component({
    selector: 'jhi-exercise-categories',
    templateUrl: './exercise-categories.component.html',
    styleUrls: ['./exercise-categories.component.scss'],
})
export class ExerciseCategoriesComponent {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly dayjs = dayjs;

    @Input() exercise: Exercise;

    @Input()
    showTags: ShowTagsConfig = {
        notReleased: false,
        quizLive: false,
        difficulty: false,
        difficultyIfNoLevel: false,
        includedInScore: false,
    };

    asQuizExercise(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }
}
