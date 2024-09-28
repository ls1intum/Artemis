import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { HttpServerRequests } from 'app/admin/metrics/metrics.model';
import { filterNaN } from 'app/core/util/operators';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { DecimalPipe, KeyValuePipe } from '@angular/common';

@Component({
    selector: 'jhi-metrics-request',
    templateUrl: './metrics-request.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
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

    filterNaN = (input: number): number => filterNaN(input);
}
