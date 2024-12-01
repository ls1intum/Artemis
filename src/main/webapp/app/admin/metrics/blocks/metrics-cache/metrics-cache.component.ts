import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CacheMetrics } from 'app/admin/metrics/metrics.model';
import { filterNaN } from 'app/core/util/operators';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DecimalPipe, KeyValuePipe } from '@angular/common';

@Component({
    selector: 'jhi-metrics-cache',
    templateUrl: './metrics-cache.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [TranslateDirective, DecimalPipe, KeyValuePipe],
})
export class MetricsCacheComponent {
    /**
     * object containing all cache related metrics
     */
    cacheMetrics = input.required<{
        [key: string]: CacheMetrics;
    }>();

    /**
     * boolean field saying if the metrics are in the process of being updated
     */
    updating = input<boolean>(false);

    filterNaN = (input: number): number => filterNaN(input);
}
