import { Component, HostBinding, Input, OnInit, OnDestroy } from '@angular/core';
import { DifficultyLevel, Exercise } from 'app/entities/exercise';
import { TranslateService, LangChangeEvent } from '@ngx-translate/core';
import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-difficulty-badge',
    templateUrl: './difficulty-badge.component.html'
})
export class DifficultyBadgeComponent implements OnInit, OnDestroy {
    @Input() exercise: Exercise;
    @Input() showNoLevel: true;
    public translatedDifficulty: string;
    public badgeClass: string;
    private translateSubscription: Subscription;

    constructor(private translateService: TranslateService) {

    }

    ngOnInit(): void {
        this.translateSubscription = this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.setBadgeAttributes();
        });
        this.setBadgeAttributes();
    }

    ngOnDestroy(): void {
        this.translateSubscription.unsubscribe();
    }

    private setBadgeAttributes() {
        switch (this.exercise.difficulty) {
            case DifficultyLevel.EASY:
                this.translatedDifficulty = this.translateService.instant('arTeMiSApp.exercise.easy');
                this.badgeClass = 'badge-success';
                break;
            case DifficultyLevel.MEDIUM:
                this.translatedDifficulty = this.translateService.instant('arTeMiSApp.exercise.medium');
                this.badgeClass = 'badge-warning';
                break;
            case DifficultyLevel.HARD:
                this.translatedDifficulty = this.translateService.instant('arTeMiSApp.exercise.hard');
                this.badgeClass = 'badge-danger';
                break;
            default:
                if (this.showNoLevel) {
                    this.badgeClass = 'badge-info';
                    this.translatedDifficulty = this.translateService.instant('arTeMiSApp.exercise.noLevel');
                }
        }
    }
}
