import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ProcessMetrics } from 'app/admin/metrics/metrics.model';
import { NgbProgressbar } from '@ng-bootstrap/ng-bootstrap';
import { DatePipe, DecimalPipe } from '@angular/common';

@Component({
    selector: 'jhi-metrics-system',
    templateUrl: './metrics-system.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    standalone: true,
    imports: [NgbProgressbar, DecimalPipe, DatePipe],
})
export class MetricsSystemComponent {
    /**
     * object containing thread related metrics
     */
    systemMetrics = input.required<ProcessMetrics>();

    /**
     * boolean field saying if the metrics are in the process of being updated
     */
    updating = input<boolean>(false);

    convertMillisecondsToDuration(ms: number): string {
        const times = {
            year: 31557600000,
            month: 2629746000,
            day: 86400000,
            hour: 3600000,
            minute: 60000,
            second: 1000,
        };
        let timeString = '';
        for (const [key, value] of Object.entries(times)) {
            if (Math.floor(ms / value) > 0) {
                let plural = '';
                if (Math.floor(ms / value) > 1) {
                    plural = 's';
                }
                timeString += `${Math.floor(ms / value).toString()} ${key.toString()}${plural} `;
                ms = ms - value * Math.floor(ms / value);
            }
        }
        return timeString;
    }
}
