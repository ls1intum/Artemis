import { Component, Input } from '@angular/core';

import { JvmMetrics } from 'app/admin/metrics/metrics.model';

@Component({
    selector: 'jhi-jvm-memory',
    templateUrl: './jvm-memory.component.html',
})
export class JvmMemoryComponent {
    /**
     * object containing all jvm memory metrics
     */
    @Input() jvmMemoryMetrics?: { [key: string]: JvmMetrics };

    /**
     * boolean field saying if the metrics are in the process of being updated
     */
    @Input() updating?: boolean;

    fun2() {
        for (const i in this.jvmMemoryMetrics) {
            console.log(i);
        }
        console.log(Object.keys(this.jvmMemoryMetrics!));
    }

    protected readonly Object = Object;
}
