import { Component, input } from '@angular/core';
import { NgClass } from '@angular/common';
import { Exercise, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { NgStyle } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IncludedInScoreBadgeComponent } from '../exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { DifficultyBadgeComponent } from '../exercise-headers/difficulty-badge/difficulty-badge.component';
import { TruncatePipe } from 'app/foundation/pipes/truncate.pipe';
import { NotReleasedTagComponent } from 'app/shared-ui/components/not-released-tag/not-released-tag.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

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
    imports: [NgClass, RouterLink, NotReleasedTagComponent, TranslateDirective, IncludedInScoreBadgeComponent, NgStyle, DifficultyBadgeComponent, TruncatePipe],
})
export class ExerciseCategoriesComponent {
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly dayjs = dayjs;

    readonly exercise = input.required<Exercise>();
    readonly isSmall = input(false);
    readonly showTags = input<ShowTagsConfig>({
        notReleased: false,
        quizLive: false,
        difficulty: false,
        difficultyIfNoLevel: false,
        includedInScore: false,
    });

    asQuizExercise(exercise: Exercise): QuizExercise {
        return exercise as QuizExercise;
    }
}
