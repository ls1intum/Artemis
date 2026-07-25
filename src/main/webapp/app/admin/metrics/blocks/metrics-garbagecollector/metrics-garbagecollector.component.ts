import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { GarbageCollector } from 'app/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumUiProgressBarComponent } from 'app/shared-ui/tum-ui/progress-bar/tum-ui-progress-bar.component';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';
import { DecimalPipe } from '@angular/common';
import { toPercentage } from 'app/admin/metrics/filterNaN-util';

@Component({
    selector: 'jhi-metrics-garbagecollector',
    templateUrl: './metrics-garbagecollector.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, TumUiProgressBarComponent, TumUiTableDirective, DecimalPipe],
})
export class MetricsGarbageCollectorComponent {
    /**
     * object containing garbage collector related metrics
     */
    garbageCollectorMetrics = input.required<GarbageCollector>();

    /**
     * boolean field saying if the metrics are in the process of being updated
     */
    updating = input<boolean>(false);

    protected readonly toPercentage = toPercentage;
}
