import { Component, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiMetricsService } from 'app/admin/metrics/metrics.service';

@Component({
    selector: 'jhi-metrics',
    templateUrl: './metrics.component.html',
})
export class JhiMetricsMonitoringComponent implements OnInit {
    metrics: any = {};
    threadData: any = {};
    updatingMetrics = true;
    JCACHE_KEY: string;

    constructor(private modalService: NgbModal, private metricsService: JhiMetricsService) {
        this.JCACHE_KEY = 'jcache.statistics';
    }

    /**
     * Calls the refresh method on init
     */
    ngOnInit() {
        this.refresh();
    }

    /**
     * Refreshes the metrics by retrieving all metrics and thread dumps
     */
    refresh() {
        this.updatingMetrics = true;
        this.metricsService.getMetrics().subscribe((metrics) => {
            this.metrics = metrics;
            this.metricsService.threadDump().subscribe((data) => {
                this.threadData = data.threads;
                this.updatingMetrics = false;
            });
        });
    }

    /**
     * Checks if the metric with the key {@param key} exists
     * @param metrics json with metrics objects
     * @param key string identifier of a metric
     */
    isObjectExisting(metrics: any, key: string) {
        return metrics && metrics[key];
    }

    /**
     * Checks if the metric with the key {@param key} exists and is not empty
     * @param metrics json with metrics objects
     * @param key key string identifier of a metric
     */
    isObjectExistingAndNotEmpty(metrics: any, key: string) {
        return this.isObjectExisting(metrics, key) && JSON.stringify(metrics[key]) !== '{}';
    }
}
