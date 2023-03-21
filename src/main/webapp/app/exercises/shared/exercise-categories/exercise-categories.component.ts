import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { DifficultyLevel, Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-exercise-categories',
    templateUrl: './exercise-categories.component.html',
    styleUrls: ['./exercise-categories.component.scss'],
})
export class ExerciseCategoriesComponent implements OnInit, OnDestroy, OnChanges {
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

    translatedDifficulty: string;
    difficultyBadgeClass: string;

    translatedIncludedAsBonus: string;
    translatedIncludedAsBonusTooltip: string;
    includedAsBonusClass: string;

    translatedNotReleased: string;
    translatedNotReleasedTooltip: string;

    private translateSubscription: Subscription;

    constructor(private translateService: TranslateService) {}

    ngOnInit(): void {
        this.translateSubscription = this.translateService.onLangChange.subscribe(() => {
            this.translateDifficulty();
            this.translateIncludedAsBonus();
            this.translateNotReleased();
        });
    }

    ngOnChanges(): void {
        this.translateDifficulty();
        this.translateIncludedAsBonus();
        this.translateNotReleased();
    }

    ngOnDestroy(): void {
        if (this.translateSubscription) {
            this.translateSubscription.unsubscribe();
        }
    }

    private translateDifficulty() {
        switch (this.exercise.difficulty) {
            case DifficultyLevel.EASY:
                this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.easy');
                this.difficultyBadgeClass = 'bg-success';
                break;
            case DifficultyLevel.MEDIUM:
                this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.medium');
                this.difficultyBadgeClass = 'bg-warning';
                break;
            case DifficultyLevel.HARD:
                this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.hard');
                this.difficultyBadgeClass = 'bg-danger';
                break;
            default:
                this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.noLevel');
        }
    }

    private translateIncludedAsBonus(): void {
        if (!this.exercise.includedInOverallScore) {
            return;
        }

        switch (this.exercise.includedInOverallScore) {
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                this.includedAsBonusClass = 'bg-warning';
                this.translatedIncludedAsBonus = this.translateService.instant('artemisApp.exercise.includedAsBonus');
                this.translatedIncludedAsBonusTooltip = this.translateService.instant('artemisApp.exercise.includedAsBonusTooltip');
                break;
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                this.includedAsBonusClass = 'bg-success';
                this.translatedIncludedAsBonus = this.translateService.instant('artemisApp.exercise.includedCompletely');
                this.translatedIncludedAsBonusTooltip = this.translateService.instant('artemisApp.exercise.includedCompletelyTooltip');
                break;
            case IncludedInOverallScore.NOT_INCLUDED:
                this.includedAsBonusClass = 'bg-secondary';
                this.translatedIncludedAsBonus = this.translateService.instant('artemisApp.exercise.notIncluded');
                this.translatedIncludedAsBonusTooltip = this.translateService.instant('artemisApp.exercise.notIncludedTooltip');
                break;
        }
    }

    private translateNotReleased() {
        this.translatedNotReleased = this.translateService.instant('artemisApp.courseOverview.exerciseList.notReleased');
        this.translatedNotReleasedTooltip = this.translateService.instant('artemisApp.courseOverview.exerciseList.notReleasedTooltip');
    }
}
