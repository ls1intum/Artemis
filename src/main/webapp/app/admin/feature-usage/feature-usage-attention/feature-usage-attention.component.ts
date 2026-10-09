import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { faChartLine } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonDirective, TumAetUiTableDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

import { FeatureUsageStatus, UserFeatureUsage } from '../feature-usage.model';
import { areaTranslationKey, featureTranslationKey } from '../feature-usage.util';

/** One group of features that each need the same decision. */
interface AttentionSection {
    key: 'onlyAutomatic' | 'unused' | 'noActions';
    features: UserFeatureUsage[];
}

/**
 * The features a decision starts from, grouped by the question they raise.
 *
 * Features this deployment does not offer are never listed: their zero usage is not a finding, because nobody could have
 * used them. Everything else is in exactly one group.
 * <ul>
 * <li>Only automatic or system calls: the feature is wired into pages that people open, or called by another system, but
 * nobody used it. These used to look like the busiest features on the page.</li>
 * <li>Unused: offered, and not a single call.</li>
 * <li>Viewed, no actions: people look at what the feature shows, but never take the step it exists for.</li>
 * </ul>
 */
@Component({
    selector: 'jhi-feature-usage-attention',
    templateUrl: './feature-usage-attention.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, TranslateDirective, ArtemisTranslatePipe, TumAetUiButtonDirective, TumAetUiTableDirective, TumAetUiTooltipDirective],
})
export class FeatureUsageAttentionComponent {
    readonly features = input.required<UserFeatureUsage[]>();

    readonly trendRequested = output<UserFeatureUsage>();

    protected readonly faChartLine = faChartLine;
    protected readonly featureTranslationKey = featureTranslationKey;
    protected readonly areaTranslationKey = areaTranslationKey;

    readonly sections = computed<AttentionSection[]>(() => [
        { key: 'onlyAutomatic', features: this.features().filter((feature) => feature.status === FeatureUsageStatus.ONLY_AUTOMATIC) },
        { key: 'unused', features: this.features().filter((feature) => feature.status === FeatureUsageStatus.UNUSED) },
        { key: 'noActions', features: this.features().filter((feature) => feature.noActions) },
    ]);
}
