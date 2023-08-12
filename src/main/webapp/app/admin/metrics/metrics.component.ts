import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { combineLatest } from 'rxjs';
import { faSync } from '@fortawesome/free-solid-svg-icons';

import { MetricsService } from './metrics.service';
import { Metrics, Thread } from 'app/admin/metrics/metrics.model';

@Component({
    selector: 'jhi-metrics',
    templateUrl: './metrics.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetricsComponent implements OnInit {
    metrics?: Metrics;
    threads?: Thread[];
    updatingMetrics = true;

    // Icons
    faSync = faSync;

    constructor(private metricsService: MetricsService, private changeDetector: ChangeDetectorRef) {}

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
