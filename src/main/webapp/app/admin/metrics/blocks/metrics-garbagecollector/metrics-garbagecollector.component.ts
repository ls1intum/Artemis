import { Component, input } from '@angular/core';
import { GarbageCollector } from 'app/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProgressBarModule } from 'primeng/progressbar';
import { DecimalPipe } from '@angular/common';
import { toPercentage } from 'app/admin/metrics/filterNaN-util';

@Component({
    selector: 'jhi-metrics-garbagecollector',
    templateUrl: './metrics-garbagecollector.component.html',
    imports: [TranslateDirective, ProgressBarModule, DecimalPipe],
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
