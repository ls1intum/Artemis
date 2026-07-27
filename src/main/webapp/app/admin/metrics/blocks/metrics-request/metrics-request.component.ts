import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { HttpServerRequests } from 'app/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumUiProgressBarComponent } from 'app/shared-ui/tum-ui/progress-bar/tum-ui-progress-bar.component';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';
import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { filterNaN, toPercentage } from 'app/admin/metrics/filterNaN-util';

@Component({
    selector: 'jhi-metrics-request',
    templateUrl: './metrics-request.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, TumUiProgressBarComponent, TumUiTableDirective, DecimalPipe, KeyValuePipe],
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
    protected readonly toPercentage = toPercentage;
}
