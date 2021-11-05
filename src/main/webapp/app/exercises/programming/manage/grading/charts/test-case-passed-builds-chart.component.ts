import { Component, Input, OnChanges } from '@angular/core';
import { TestCaseStats } from 'app/entities/programming-exercise-test-case-statistics.model';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-test-case-passed-builds-chart',
    template: `
        <div class="chart-body" placement="left" container="body" [ngbTooltip]="tooltip">
            <div class="passed-bar" [style]="{ width: passedPercent + '%' }"></div>
            <div class="failed-bar" [style]="{ left: passedPercent + '%', width: failedPercent + '%' }"></div>
        </div>
    `,
    styles: [
        '.chart-body { border-radius: 4px; background-color: #999; height: 10px; width: 100px; overflow: hidden; position: relative; }',
        '.passed-bar { position: absolute; top: 0; left: 0; height: 10px; background-color: #28a745 }',
        '.failed-bar { position: absolute; top: 0; height: 10px; background-color: #dc3545 }',
    ],
})
export class TestCasePassedBuildsChartComponent implements OnChanges {
    @Input() testCaseStats?: TestCaseStats;
    @Input() totalParticipations: number | undefined;

    passedPercent = 0;
    failedPercent = 0;
    tooltip = '';

    ngOnChanges(): void {
        if (this.totalParticipations && this.totalParticipations > 0) {
            const passedPercent = ((this.testCaseStats?.numPassed || 0) / this.totalParticipations) * 100;
            const failedPercent = ((this.testCaseStats?.numFailed || 0) / this.totalParticipations) * 100;
            const notExecutedPercent = round(100 - passedPercent - failedPercent);

            setTimeout(() => {
                this.passedPercent = passedPercent;
                this.failedPercent = failedPercent;

                this.tooltip = `${passedPercent.toFixed(0)}% passed, ${failedPercent.toFixed(0)}% failed${
                    notExecutedPercent > 0 ? `, ${notExecutedPercent}% not executed` : ''
                } of ${this.totalParticipations} students.`;
            });
        }
    }
}
