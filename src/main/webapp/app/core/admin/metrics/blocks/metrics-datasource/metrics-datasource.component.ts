import { Component, input } from '@angular/core';
import { Databases } from 'app/core/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DecimalPipe } from '@angular/common';
import { filterNaN } from 'app/core/admin/metrics/filterNaN-util';

@Component({
    selector: 'jhi-metrics-datasource',
    templateUrl: './metrics-datasource.component.html',
    imports: [TranslateDirective, DecimalPipe],
})
export class MetricsDatasourceComponent {
    /**
     * object containing all datasource related metrics
     */
    datasourceMetrics = input.required<Databases>();

    /**
     * boolean field saying if the metrics are in the process of being updated
     */
    updating = input<boolean>(false);

    protected readonly filterNaN = filterNaN;
}
