import { Component, OnInit } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

import { JhiMetricsMonitoringModalComponent } from './metrics-modal.component';
import { JhiMetricsService } from './metrics.service';

@Component({
    selector: 'jhi-metrics',
    templateUrl: './metrics.component.html'
})
export class JhiMetricsMonitoringComponent implements OnInit {
    metrics: any = {};
    cachesStats: any = {};
    servicesStats: any = {};
    updatingMetrics = true;
    JCACHE_KEY: string;

    constructor(private modalService: NgbModal, private metricsService: JhiMetricsService) {
        this.JCACHE_KEY = 'jcache.statistics';
    }

    ngOnInit() {
        this.refresh();
    }

    refresh() {
        this.updatingMetrics = true;
        this.metricsService.getMetrics().subscribe((metrics: any) => {
            this.metrics = metrics;
            this.updatingMetrics = false;
            this.servicesStats = {};
            this.cachesStats = {};
            Object.keys(metrics.timers).forEach((key: string) => {
                const value = metrics.timers[key];
                if (key.includes('web.rest') || key.includes('service')) {
                    this.servicesStats[key] = value;
                }
            });
            Object.keys(metrics.gauges).forEach((key: string) => {
                if (key.includes('jcache.statistics')) {
                    const value = metrics.gauges[key].value;
                    // remove gets or puts
                    const index = key.lastIndexOf('.');
                    const newKey = key.substr(0, index);

                    // Keep the name of the domain
                    this.cachesStats[newKey] = {
                        name: this.JCACHE_KEY.length,
                        value: value
                    };
                }
            });
        });
    }

    refreshThreadDumpData() {
        this.metricsService.threadDump().subscribe((data: any) => {
            const modalRef = this.modalService.open(JhiMetricsMonitoringModalComponent, { size: 'lg' });
            modalRef.componentInstance.threadDump = data;
            modalRef.result.then(
                (result: any) => {
                    // Left blank intentionally, nothing to do here
                },
                (reason: any) => {
                    // Left blank intentionally, nothing to do here
                }
            );
        });
    }

    filterNaN(input: number) {
        if (isNaN(input)) {
            return 0;
        }
        return input;
    }
}
