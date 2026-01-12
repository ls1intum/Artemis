import { Component, input } from '@angular/core';
import { CacheMetrics } from 'app/core/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { filterNaN } from 'app/core/admin/metrics/filterNaN-util';

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
