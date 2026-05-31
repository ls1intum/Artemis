import { Component, input } from '@angular/core';
import { CacheMetrics } from 'app/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { filterNaN } from 'app/admin/metrics/filterNaN-util';

@Component({
    selector: 'jhi-metrics-cache',
    templateUrl: './metrics-cache.component.html',
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
    protected readonly filterNaN = filterNaN;
}
