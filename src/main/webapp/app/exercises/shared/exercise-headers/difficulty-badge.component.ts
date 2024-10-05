import { Component, Input, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { DifficultyLevel, Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-difficulty-badge',
    templateUrl: './difficulty-badge.component.html',
})
export class DifficultyBadgeComponent implements OnInit, OnDestroy, OnChanges {
    private translateService = inject(TranslateService);

    @Input() exercise: Exercise;
    @Input() showNoLevel: boolean;
    public translatedDifficulty: string;
    public badgeClass: string;
    private translateSubscription: Subscription;

    /**
     * Sets the badge attributes based on the exercise difficulty
     */
    ngOnInit(): void {
        this.translateSubscription = this.translateService.onLangChange.subscribe(() => {
            this.setBadgeAttributes();
        });
    }

    ngOnChanges(): void {
        this.setBadgeAttributes();
    }

    /**
     * Cleans up the subscription to the translation service
     */
    ngOnDestroy(): void {
        if (this.translateSubscription) {
            this.translateSubscription.unsubscribe();
        }
    }

    private setBadgeAttributes() {
        switch (this.exercise.difficulty) {
            case DifficultyLevel.EASY:
                this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.easy');
                this.badgeClass = 'bg-success';
                break;
            case DifficultyLevel.MEDIUM:
                this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.medium');
                this.badgeClass = 'bg-warning';
                break;
            case DifficultyLevel.HARD:
                this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.hard');
                this.badgeClass = 'bg-danger';
                break;
            default:
                if (this.showNoLevel) {
                    this.badgeClass = 'bg-info';
                    this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.noLevel');
                }
        }
    }
}
