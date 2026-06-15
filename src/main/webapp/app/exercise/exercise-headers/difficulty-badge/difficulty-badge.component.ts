import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { DifficultyLevel, Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-difficulty-badge',
    templateUrl: './difficulty-badge.component.html',
    imports: [NgClass],
})
export class DifficultyBadgeComponent {
    private translateService = inject(TranslateService);

    readonly exercise = input.required<Exercise>();
    readonly showNoLevel = input<boolean>(false);

    private readonly currentLang = toSignal(this.translateService.onLangChange, { initialValue: undefined });

    readonly translatedDifficulty = computed<string | undefined>(() => {
        // read the language signal to recompute the translation on language change
        this.currentLang();
        switch (this.exercise().difficulty) {
            case DifficultyLevel.EASY:
                return this.translateService.instant('artemisApp.exercise.easy');
            case DifficultyLevel.MEDIUM:
                return this.translateService.instant('artemisApp.exercise.medium');
            case DifficultyLevel.HARD:
                return this.translateService.instant('artemisApp.exercise.hard');
            default:
                if (this.showNoLevel()) {
                    return this.translateService.instant('artemisApp.exercise.noLevel');
                }
                return undefined;
        }
    });

    readonly badgeClass = computed<string | undefined>(() => {
        switch (this.exercise().difficulty) {
            case DifficultyLevel.EASY:
                return 'bg-success';
            case DifficultyLevel.MEDIUM:
                return 'bg-warning';
            case DifficultyLevel.HARD:
                return 'bg-danger';
            default:
                if (this.showNoLevel()) {
                    return 'bg-info';
                }
                return undefined;
        }
    });
}
