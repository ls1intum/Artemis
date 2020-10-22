import { Component, Input, OnChanges } from '@angular/core';
import { TestCaseStats } from 'app/entities/programming-exercise-test-case-statistics.model';

@Component({
    selector: 'jhi-test-case-passed-builds-chart',
    template: `
        <div
            class="chart-body"
            placement="left"
            container="body"
            [ngbTooltip]="passedPercent.toFixed(0) + '% passed, ' + failedPercent.toFixed(0) + '% failed of ' + totalParticipations + ' students.'"
        >
            <div class="passed-bar" [style]="{ width: passedPercent + '%' }"></div>
            <div class="failed-bar" [style]="{ left: passedPercent + '%', width: failedPercent + '%' }"></div>
        </div>
    `,
    styles: [
        '.chart-body { border-radius: 4px; background-color: #ddd; height: 10px; width: 100px; overflow: hidden; position: relative; }',
        '.passed-bar { position: absolute; top: 0; left: 0; height: 10px; color: #28a745 }',
        '.failed-bar { position: absolute; top: 0; height: 10px; color: #dc3545 }',
    ],
})
export class TestCasePassedBuildsChartComponent implements OnChanges {
    @Input() testCaseStats?: TestCaseStats;
    @Input() totalParticipations: number;

    passedPercent = 0;
    failedPercent = 0;

    ngOnChanges(): void {
        const passedPercent = this.totalParticipations > 0 ? ((this.testCaseStats?.numPassed || 0) / this.totalParticipations) * 100 : 0;
        const failedPercent = this.totalParticipations > 0 ? ((this.testCaseStats?.numFailed || 0) / this.totalParticipations) * 100 : 0;

        setTimeout(() => {
            this.passedPercent = passedPercent;
            this.failedPercent = failedPercent;
        });
    }
}
