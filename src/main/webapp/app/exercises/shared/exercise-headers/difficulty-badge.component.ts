import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs/Subscription';
import { DifficultyLevel, Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-difficulty-badge',
    templateUrl: './difficulty-badge.component.html',
})
export class DifficultyBadgeComponent implements OnInit, OnDestroy {
    @Input() exercise: Exercise;
    @Input() showNoLevel: true;
    public translatedDifficulty: string;
    public badgeClass: string;
    private translateSubscription: Subscription;

    constructor(private translateService: TranslateService) {}

    ngOnInit(): void {
        this.translateSubscription = this.translateService.onLangChange.subscribe(() => {
            this.setBadgeAttributes();
        });
        this.setBadgeAttributes();
    }

    ngOnDestroy(): void {
        if (this.translateSubscription) {
            this.translateSubscription.unsubscribe();
        }
    }

    private setBadgeAttributes() {
        switch (this.exercise.difficulty) {
            case DifficultyLevel.EASY:
                this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.easy');
                this.badgeClass = 'badge-success';
                break;
            case DifficultyLevel.MEDIUM:
                this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.medium');
                this.badgeClass = 'badge-warning';
                break;
            case DifficultyLevel.HARD:
                this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.hard');
                this.badgeClass = 'badge-danger';
                break;
            default:
                if (this.showNoLevel) {
                    this.badgeClass = 'badge-info';
                    this.translatedDifficulty = this.translateService.instant('artemisApp.exercise.noLevel');
                }
        }
    }
}
