import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { faChartLine, faChevronDown, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonDirective, TumAetUiTableDirective, TumAetUiTagComponent, TumAetUiTagSeverity, TumAetUiTooltipDirective } from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

import { FeatureAdoption, FeatureInteraction, FeatureKind, FeatureTreeRow, FeatureUsageEndpoint, FeatureUsageStatus, UserFeatureUsage } from '../feature-usage.model';
import { areaTranslationKey, buildFeatureTree, expandableKeys, featureTranslationKey, verbOf } from '../feature-usage.util';

/** What a row of the tree asks the page to chart. */
export type FeatureUsageTrendRequest = { feature: UserFeatureUsage } | { endpoint: FeatureUsageEndpoint };

const STATUS_SEVERITY: Record<FeatureUsageStatus, TumAetUiTagSeverity> = {
    [FeatureUsageStatus.USED]: 'success',
    [FeatureUsageStatus.ONLY_AUTOMATIC]: 'warn',
    [FeatureUsageStatus.UNUSED]: 'warn',
    [FeatureUsageStatus.NOT_AVAILABLE]: 'secondary',
};

const INTERACTION_SEVERITY: Record<FeatureInteraction, TumAetUiTagSeverity> = {
    [FeatureInteraction.ACTION]: 'info',
    [FeatureInteraction.VIEW]: 'secondary',
    [FeatureInteraction.AUTOMATIC]: 'contrast',
    [FeatureInteraction.SYSTEM]: 'contrast',
};

/**
 * The feature tree: product area, feature, the module and controller behind it, and the individual endpoints.
 *
 * This is the view the page leads with, because it reads the way users think of Artemis: an area such as "Exams" opens to
 * the features an instructor or a student would name, and only one level further down to the code that implements them.
 * Actions and views are shown in columns of their own, and automatic calls in a third, so a status probe on a busy page
 * can no longer pass for use.
 */
@Component({
    selector: 'jhi-feature-usage-tree',
    templateUrl: './feature-usage-tree.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        DecimalPipe,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumAetUiButtonDirective,
        TumAetUiTableDirective,
        TumAetUiTagComponent,
        TumAetUiTooltipDirective,
    ],
})
export class FeatureUsageTreeComponent {
    readonly features = input.required<UserFeatureUsage[]>();
    readonly endpointsByFeature = input.required<Map<string, FeatureUsageEndpoint[]>>();
    readonly adoptionByFeature = input<Map<string, FeatureAdoption[]>>(new Map());

    readonly trendRequested = output<FeatureUsageTrendRequest>();

    protected readonly faChevronRight = faChevronRight;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faChartLine = faChartLine;
    protected readonly FeatureKind = FeatureKind;
    protected readonly FeatureUsageStatus = FeatureUsageStatus;
    protected readonly featureTranslationKey = featureTranslationKey;
    protected readonly areaTranslationKey = areaTranslationKey;
    protected readonly verbOf = verbOf;

    /** Keys of the expanded rows. Everything starts collapsed: every area expanded at once is not a summary. */
    readonly expandedKeys = signal<ReadonlySet<string>>(new Set());

    readonly rows = computed<FeatureTreeRow[]>(() => buildFeatureTree(this.features(), this.endpointsByFeature(), this.adoptionByFeature(), this.expandedKeys()));

    toggle(key: string): void {
        const expanded = new Set(this.expandedKeys());
        if (!expanded.delete(key)) {
            expanded.add(key);
        }
        this.expandedKeys.set(expanded);
    }

    /** Opens every area, feature and resource, for when the whole catalogue needs scanning rather than exploring. */
    expandAll(): void {
        this.expandedKeys.set(expandableKeys(this.features(), this.endpointsByFeature()));
    }

    collapseAll(): void {
        this.expandedKeys.set(new Set());
    }

    statusSeverity(status: FeatureUsageStatus): TumAetUiTagSeverity {
        return STATUS_SEVERITY[status];
    }

    interactionSeverity(interaction: FeatureInteraction): TumAetUiTagSeverity {
        return INTERACTION_SEVERITY[interaction];
    }

    /** The mean duration of the calls that count as use, or undefined when there were none. */
    meanDuration(row: FeatureTreeRow): number | undefined {
        const calls = row.kind === 'endpoint' ? (row.endpoint?.callCount ?? 0) : row.actionCount + row.viewCount;
        return calls ? row.durationSumMs / calls : undefined;
    }

    /** The error rate of the calls that count as use, or undefined when there were none. */
    errorRate(row: FeatureTreeRow): number | undefined {
        const calls = row.kind === 'endpoint' ? (row.endpoint?.callCount ?? 0) : row.actionCount + row.viewCount;
        return calls ? (row.errorCount / calls) * 100 : undefined;
    }

    /** Whether the row saw anything to chart. */
    hasCalls(row: FeatureTreeRow): boolean {
        return row.actionCount + row.viewCount + row.automaticCount + row.systemCount > 0;
    }

    showTrend(row: FeatureTreeRow): void {
        if (row.feature) {
            this.trendRequested.emit({ feature: row.feature });
        } else if (row.endpoint) {
            this.trendRequested.emit({ endpoint: row.endpoint });
        }
    }
}
