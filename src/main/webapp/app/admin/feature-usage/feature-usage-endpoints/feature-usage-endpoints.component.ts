import { ChangeDetectionStrategy, Component, computed, input, linkedSignal, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { faChartLine } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    TumUiButtonDirective,
    TumUiSelectComponent,
    TumUiTableDirective,
    TumUiTableSortEvent,
    TumUiTableSortableColumnComponent,
    TumUiTagComponent,
    TumUiTagSeverity,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

import { FeatureInteraction, FeatureUsageEndpoint } from '../feature-usage.model';
import { featureTranslationKey, pathOf, verbOf } from '../feature-usage.util';

type SortField = 'identifier' | 'interaction' | 'featureLabel' | 'callCount' | 'activeDays' | 'errorRate' | 'meanDurationMs' | 'durationMaxMs' | 'lastUsedDay';

/** An endpoint with the values the table sorts on that the server does not send. */
interface EndpointRow {
    endpoint: FeatureUsageEndpoint;
    errorRate: number;
    meanDurationMs: number;
}

const ALL_MODULES = '';

const INTERACTION_SEVERITY: Record<FeatureInteraction, TumUiTagSeverity> = {
    [FeatureInteraction.ACTION]: 'info',
    [FeatureInteraction.VIEW]: 'secondary',
    [FeatureInteraction.AUTOMATIC]: 'contrast',
    [FeatureInteraction.SYSTEM]: 'contrast',
};

/**
 * Every inventory entry, as a flat sortable list: the technical view for developers, who think in controllers and paths
 * rather than in features. It is also the only place that lists entries belonging to no catalogue feature, which only
 * happens for endpoints an earlier version offered.
 */
@Component({
    selector: 'jhi-feature-usage-endpoints',
    templateUrl: './feature-usage-endpoints.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        TumUiButtonDirective,
        TumUiSelectComponent,
        TumUiTableDirective,
        TumUiTableSortableColumnComponent,
        TumUiTagComponent,
    ],
})
export class FeatureUsageEndpointsComponent {
    readonly endpoints = input.required<FeatureUsageEndpoint[]>();
    /** The catalogue features, so an endpoint can be shown by the name of the feature it serves. */
    readonly knownFeatures = input.required<ReadonlySet<string>>();
    readonly allModulesLabel = input<string>('');

    readonly trendRequested = output<FeatureUsageEndpoint>();

    protected readonly faChartLine = faChartLine;
    protected readonly featureTranslationKey = featureTranslationKey;
    protected readonly verbOf = verbOf;
    protected readonly pathOf = pathOf;

    readonly sortField = signal<SortField>('callCount');
    readonly sortAscending = signal<boolean>(false);

    readonly moduleOptions = computed(() => [
        { label: this.allModulesLabel(), value: ALL_MODULES },
        ...[...new Set(this.endpoints().map((endpoint) => endpoint.module))]
            .sort((first, second) => first.localeCompare(second))
            .map((module) => ({ label: module, value: module })),
    ]);

    /**
     * Follows the options: the page's search and area filter decide which endpoints arrive here, and a module that is no
     * longer among them falls back to all modules. Kept as it was, it would leave an empty select above an empty table.
     */
    readonly selectedModule = linkedSignal<{ value: string }[], string>({
        source: this.moduleOptions,
        computation: (options, previous) => (previous && options.some((option) => option.value === previous.value) ? previous.value : ALL_MODULES),
    });

    readonly rows = computed<EndpointRow[]>(() => {
        const module = this.selectedModule();
        const rows = this.endpoints()
            .filter((endpoint) => module === ALL_MODULES || endpoint.module === module)
            .map((endpoint) => ({
                endpoint,
                errorRate: endpoint.callCount ? (endpoint.errorCount / endpoint.callCount) * 100 : 0,
                meanDurationMs: endpoint.callCount ? Math.round(endpoint.durationSumMs / endpoint.callCount) : 0,
            }));
        return rows.sort(this.comparator());
    });

    interactionSeverity(interaction: FeatureInteraction): TumUiTagSeverity {
        return INTERACTION_SEVERITY[interaction];
    }

    onSort(event: TumUiTableSortEvent): void {
        this.sortField.set(event.field as SortField);
        this.sortAscending.set(event.order === 1);
    }

    private comparator(): (first: EndpointRow, second: EndpointRow) => number {
        const field = this.sortField();
        const direction = this.sortAscending() ? 1 : -1;
        const valueOf = (row: EndpointRow): string | number | undefined => (field === 'errorRate' || field === 'meanDurationMs' ? row[field] : row.endpoint[field]);
        return (first, second) => {
            const firstValue = valueOf(first);
            const secondValue = valueOf(second);
            if (typeof firstValue === 'number' && typeof secondValue === 'number') {
                return (firstValue - secondValue) * direction;
            }
            // Undefined sorts last regardless of direction: an unused endpoint has no "last used" day, and letting those
            // float to the top of a descending sort would bury the rows the column is being sorted for.
            if (firstValue === undefined || secondValue === undefined) {
                return firstValue === secondValue ? 0 : firstValue === undefined ? 1 : -1;
            }
            return String(firstValue).localeCompare(String(secondValue)) * direction;
        };
    }
}
