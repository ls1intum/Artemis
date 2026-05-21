import { Component, input } from '@angular/core';
import { HttpServerRequests } from 'app/core/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { filterNaN } from 'app/core/admin/metrics/filterNaN-util';

@Component({
    selector: 'jhi-metrics-request',
    templateUrl: './metrics-request.component.html',
    imports: [TranslateDirective, NgbProgressbar, DecimalPipe, KeyValuePipe],
})
export class MetricsRequestComponent {
    /**
     * object containing http request related metrics
     */
    requestMetrics = input.required<HttpServerRequests>();

    /**
     * boolean field saying if the metrics are in the process of being updated
     */
    updating = input<boolean>(false);
    protected readonly filterNaN = filterNaN;
}
