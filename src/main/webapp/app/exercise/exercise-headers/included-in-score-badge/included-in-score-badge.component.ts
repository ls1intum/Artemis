import { TumAetUiTooltipDirective } from '@tumaet/ui-angular';

import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-included-in-score-badge',
    templateUrl: './included-in-score-badge.component.html',
    styleUrls: ['./included-in-score-badge.component.scss'],
    imports: [TumAetUiTooltipDirective, NgClass],
})
export class IncludedInScoreBadgeComponent {
    private translateService = inject(TranslateService);

    readonly includedInOverallScore = input<IncludedInOverallScore>();

    // Re-translate the labels when the active language changes. The emitted value is unused; reading it inside the
    // computeds below registers the reactive dependency so they recompute on a language switch (replacing the former
    // onLangChange subscription).
    private readonly currentLang = toSignal(this.translateService.onLangChange, { initialValue: undefined });

    readonly badgeClass = computed<string | undefined>(() => {
        switch (this.includedInOverallScore()) {
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return 'bg-state-warning';
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return 'bg-state-success';
            case IncludedInOverallScore.NOT_INCLUDED:
                return 'bg-surface-sunken';
            default:
                return undefined;
        }
    });

    readonly translatedEnum = computed<string>(() => this.translateBadge(''));
    readonly translatedTooltip = computed<string>(() => this.translateBadge('TumAetUiTooltipDirective'));

    private translateBadge(suffix: string): string {
        this.currentLang(); // register the language dependency so the label re-translates on a language switch
        switch (this.includedInOverallScore()) {
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return this.translateService.instant(`artemisApp.exercise.includedAsBonus${suffix}`);
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return this.translateService.instant(`artemisApp.exercise.includedCompletely${suffix}`);
            case IncludedInOverallScore.NOT_INCLUDED:
                return this.translateService.instant(`artemisApp.exercise.notIncluded${suffix}`);
            default:
                return '';
        }
    }
}
