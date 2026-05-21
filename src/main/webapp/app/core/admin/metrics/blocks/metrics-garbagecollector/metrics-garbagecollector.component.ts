import { Component, input } from '@angular/core';
import { GarbageCollector } from 'app/core/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { DecimalPipe } from '@angular/common';

@Component({
    selector: 'jhi-metrics-garbagecollector',
    templateUrl: './metrics-garbagecollector.component.html',
    imports: [TranslateDirective, NgbProgressbar, DecimalPipe],
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
}
