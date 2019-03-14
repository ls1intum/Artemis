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

    @HostBinding('class.badge-easy')
    public get isEasy(): boolean {
        return this.exercise.difficulty === DifficultyLevel.EASY;
    }

    @HostBinding('class.badge-medium')
    public get isMedium(): boolean {
        return this.exercise.difficulty === DifficultyLevel.MEDIUM;
    }

    @HostBinding('class.badge-hard')
    public get isHard(): boolean {
        return this.exercise.difficulty === DifficultyLevel.HARD;
    }

    constructor(private translateService: TranslateService) {

    }

    ngOnInit(): void {
        this.translateSubscription = this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.translateBadge();
        });
        this.translateBadge();
        this.setBadgeColor();
    }

    ngOnDestroy(): void {
        this.translateSubscription.unsubscribe();
    }

    private setBadgeColor() {
        switch (this.exercise.difficulty) {
            case DifficultyLevel.EASY:
                this.badgeClass = 'badge-success';
                break;
            case DifficultyLevel.MEDIUM:
                this.badgeClass = 'badge-warining';
                break;
            case DifficultyLevel.MEDIUM:
                this.badgeClass = 'badge-danger';
                break;
            default:
                if (this.showNoLevel) {
                    this.badgeClass = 'badge-info';
                    this.translatedDifficulty = this.translateService.instant('arTeMiSApp.exercise.noLevel');
                }
        }
    }

    private translateBadge() {
        switch (this.exercise.difficulty) {
            case DifficultyLevel.EASY:
                this.translatedDifficulty = this.translateService.instant('arTeMiSApp.exercise.easy');
                break;
            case DifficultyLevel.MEDIUM:
                this.translatedDifficulty = this.translateService.instant('arTeMiSApp.exercise.medium');
                break;
            case DifficultyLevel.MEDIUM:
                this.translatedDifficulty = this.translateService.instant('arTeMiSApp.exercise.hard');
                break;
            default:
                if (this.showNoLevel) {
                    this.translatedDifficulty = this.translateService.instant('arTeMiSApp.exercise.noLevel');
                }
        }

    }
}
