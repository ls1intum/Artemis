import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { JvmMetrics } from 'app/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { DecimalPipe, KeyValuePipe } from '@angular/common';

@Component({
    selector: 'jhi-jvm-memory',
    templateUrl: './jvm-memory.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [TranslateDirective, NgbProgressbar, DecimalPipe, KeyValuePipe],
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
}
