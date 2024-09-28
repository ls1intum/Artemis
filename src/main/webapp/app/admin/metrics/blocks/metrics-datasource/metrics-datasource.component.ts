import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Databases } from 'app/admin/metrics/metrics.model';
import { filterNaN } from 'app/core/util/operators';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DecimalPipe } from '@angular/common';

@Component({
    selector: 'jhi-metrics-datasource',
    templateUrl: './metrics-datasource.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
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

    filterNaN = (input: number): number => filterNaN(input);
}
