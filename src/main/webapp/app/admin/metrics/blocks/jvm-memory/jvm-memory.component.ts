import { Component, input } from '@angular/core';

import { JvmMetrics } from 'app/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProgressBarModule } from 'primeng/progressbar';
import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { toPercentage } from 'app/admin/metrics/filterNaN-util';

@Component({
    selector: 'jhi-jvm-memory',
    templateUrl: './jvm-memory.component.html',
    imports: [TranslateDirective, ProgressBarModule, DecimalPipe, KeyValuePipe],
})
export class JvmMemoryComponent {
    /**
     * object containing all jvm memory metrics
     */
    jvmMemoryMetrics = input.required<{
        [key: string]: JvmMetrics;
    }>();

    /**
     * boolean field saying if the metrics are in the process of being updated
     */
    updating = input<boolean>(false);

    protected readonly toPercentage = toPercentage;
}
