import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { combineLatest } from 'rxjs';
import { faSync } from '@fortawesome/free-solid-svg-icons';

import { MetricsService } from './metrics.service';
import { Metrics, Thread } from 'app/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JvmMemoryComponent } from './blocks/jvm-memory/jvm-memory.component';
import { JvmThreadsComponent } from './blocks/jvm-threads/jvm-threads.component';
import { MetricsSystemComponent } from './blocks/metrics-system/metrics-system.component';
import { MetricsGarbageCollectorComponent } from './blocks/metrics-garbagecollector/metrics-garbagecollector.component';
import { MetricsRequestComponent } from './blocks/metrics-request/metrics-request.component';
import { MetricsEndpointsRequestsComponent } from './blocks/metrics-endpoints-requests/metrics-endpoints-requests.component';
import { MetricsCacheComponent } from './blocks/metrics-cache/metrics-cache.component';
import { MetricsDatasourceComponent } from './blocks/metrics-datasource/metrics-datasource.component';

@Component({
    selector: 'jhi-metrics',
    templateUrl: './metrics.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [
        TranslateDirective,
        FaIconComponent,
        JvmMemoryComponent,
        JvmThreadsComponent,
        MetricsSystemComponent,
        MetricsGarbageCollectorComponent,
        MetricsRequestComponent,
        MetricsEndpointsRequestsComponent,
        MetricsCacheComponent,
        MetricsDatasourceComponent,
    ],
})
export class MetricsComponent implements OnInit {
    private metricsService = inject(MetricsService);
    private changeDetector = inject(ChangeDetectorRef);

    metrics?: Metrics;
    threads: Thread[] = [];
    updatingMetrics = true;

    // Icons
    faSync = faSync;

    /**
     * Calls the refresh method on init
     */
    ngOnInit() {
        this.refresh();
    }

    /**
     * Refreshes the metrics by retrieving all metrics and thread dumps
     */
    refresh(): void {
        this.updatingMetrics = true;
        combineLatest([this.metricsService.getMetrics(), this.metricsService.threadDump()]).subscribe(([metrics, threadDump]) => {
            this.metrics = metrics;
            this.threads = threadDump.threads;
            this.updatingMetrics = false;
            this.changeDetector.markForCheck();
        });
    }

    /**
     * Checks if the metric with the key {@param key} exists
     * @param key string identifier of a metric
     */
    metricsKeyExists(key: keyof Metrics): boolean {
        return Boolean(this.metrics?.[key]);
    }

    /**
     * Checks if the metric with the key {@param key} exists and is not empty
     * @param key key string identifier of a metric
     */
    metricsKeyExistsAndObjectNotEmpty(key: keyof Metrics): boolean {
        return Boolean(this.metrics?.[key] && JSON.stringify(this.metrics[key]) !== '{}');
    }
}
