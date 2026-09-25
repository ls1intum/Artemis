import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { TumAetUiProgressBarComponent, TumAetUiTableDirective } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

import { FeatureAdoption, PRODUCT_AREAS, UserFeatureUsage } from '../feature-usage.model';
import { areaTranslationKey, featureTranslationKey } from '../feature-usage.util';

/** An adoption entry together with the feature it switches on, as far as that is known. */
interface AdoptionRow {
    entry: FeatureAdoption;
    area?: string;
    sharePercent: number;
}

/**
 * How many courses, exercises and exams have each optional setting switched on, listed under the feature it switches on.
 *
 * Not usage at all, but the other half of reading it: a feature switched on everywhere and never used needs a different
 * decision from one nobody switched on. Listing adoption by feature, rather than by the module that stores the setting,
 * puts both halves next to each other.
 */
@Component({
    selector: 'jhi-feature-usage-adoption',
    templateUrl: './feature-usage-adoption.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [DecimalPipe, TranslateDirective, ArtemisTranslatePipe, TumAetUiProgressBarComponent, TumAetUiTableDirective],
})
export class FeatureUsageAdoptionComponent {
    readonly adoption = input.required<FeatureAdoption[]>();
    readonly features = input.required<UserFeatureUsage[]>();

    protected readonly featureTranslationKey = featureTranslationKey;
    protected readonly areaTranslationKey = areaTranslationKey;

    /** In catalogue order: by area, then by feature, so the settings of one area stay together. */
    readonly rows = computed<AdoptionRow[]>(() => {
        const catalogueOrder = new Map(this.features().map((feature, index) => [feature.feature, index]));
        const areaOf = new Map(this.features().map((feature) => [feature.feature, feature.area]));
        return this.adoption()
            .map((entry) => ({ entry, area: entry.feature ? areaOf.get(entry.feature) : undefined, sharePercent: entry.total ? (entry.count / entry.total) * 100 : 0 }))
            .sort(
                (first, second) =>
                    areaIndex(first.area) - areaIndex(second.area) ||
                    (catalogueOrder.get(first.entry.feature ?? '') ?? Number.MAX_SAFE_INTEGER) - (catalogueOrder.get(second.entry.feature ?? '') ?? Number.MAX_SAFE_INTEGER) ||
                    first.entry.key.localeCompare(second.entry.key),
            );
    });
}

function areaIndex(area: string | undefined): number {
    const index = area ? PRODUCT_AREAS.indexOf(area) : -1;
    return index < 0 ? PRODUCT_AREAS.length : index;
}
