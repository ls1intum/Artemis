import { Component, Input } from '@angular/core';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { NgClass, NgStyle } from '@angular/common';
import { RouterLink } from '@angular/router';
import { NotReleasedTagComponent } from '../components/not-released-tag.component';
import { TranslateDirective } from '../language/translate.directive';
import { IncludedInScoreBadgeComponent } from '../../exercise/exercise-headers/included-in-score-badge.component';
import { DifficultyBadgeComponent } from '../../exercise/exercise-headers/difficulty-badge.component';
import { TruncatePipe } from 'app/shared/pipes/truncate.pipe';

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

    @Input() exercise: Exercise;
    @Input() isSmall = false;

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
