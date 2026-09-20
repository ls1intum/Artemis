import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { JvmMetrics } from 'app/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumUiProgressBarComponent } from '@tumaet/ui-angular';
import { DecimalPipe, KeyValuePipe } from '@angular/common';
import { toPercentage } from 'app/admin/metrics/filterNaN-util';

@Component({
    selector: 'jhi-jvm-memory',
    templateUrl: './jvm-memory.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, TumUiProgressBarComponent, DecimalPipe, KeyValuePipe],
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
