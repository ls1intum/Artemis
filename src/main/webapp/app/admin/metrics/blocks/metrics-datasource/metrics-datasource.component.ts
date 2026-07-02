import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Databases } from 'app/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DecimalPipe } from '@angular/common';
import { filterNaN } from 'app/admin/metrics/filterNaN-util';
import { TableModule } from 'primeng/table';

@Component({
    selector: 'jhi-metrics-datasource',
    templateUrl: './metrics-datasource.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, TableModule, DecimalPipe],
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
