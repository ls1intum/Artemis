import { Component, input } from '@angular/core';
import { Services } from 'app/core/admin/metrics/metrics.model';
import { DecimalPipe, KeyValuePipe } from '@angular/common';

@Component({
    selector: 'jhi-metrics-endpoints-requests',
    templateUrl: './metrics-endpoints-requests.component.html',
    imports: [DecimalPipe, KeyValuePipe],
})
export class MetricsEndpointsRequestsComponent {
    /**
     * object containing service related metrics
     */
    endpointsRequestsMetrics = input.required<Services>();

    /**
     * boolean field saying if the metrics are in the process of being updated
     */
    updating = input<boolean>(false);
}
