import { Component, computed, input } from '@angular/core';
import { TestCaseStats } from 'app/programming/shared/entities/programming-exercise-test-case-statistics.model';
import { round } from 'app/foundation/util/utils';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-test-case-passed-builds-chart',
    template: `
        <div class="chart-body" placement="left auto" [ngbTooltip]="tooltip()">
            <div class="passed-bar" [style]="{ width: passedPercent() + '%' }"></div>
            <div class="failed-bar" [style]="{ left: passedPercent() + '%', width: failedPercent() + '%' }"></div>
        </div>
    `,
    styles: [
        '.chart-body { border-radius: 4px; background-color: #999; height: 10px; max-width: 100px; overflow: hidden; position: relative; }',
        '.passed-bar { position: absolute; top: 0; left: 0; height: 10px; background-color: #28a745 }',
        '.failed-bar { position: absolute; top: 0; height: 10px; background-color: #dc3545 }',
    ],
    imports: [NgbTooltip],
})
export class TestCasePassedBuildsChartComponent {
    readonly testCaseStats = input<TestCaseStats>();
    readonly totalParticipations = input.required<number>();

    private readonly chartData = computed(() => {
        const testCaseStats = this.testCaseStats();
        const totalPassedAndFailed = (testCaseStats?.numPassed ?? 0) + (testCaseStats?.numFailed ?? 0);
        const totalParticipations = Math.max(totalPassedAndFailed, this.totalParticipations());

        if (totalParticipations > 0) {
            const passedPercent = ((testCaseStats?.numPassed || 0) / totalParticipations) * 100;
            const failedPercent = ((testCaseStats?.numFailed || 0) / totalParticipations) * 100;
            return { passedPercent, failedPercent, totalParticipations };
        }
        return { passedPercent: 0, failedPercent: 0, totalParticipations: 0 };
    });

    readonly passedPercent = computed(() => this.chartData().passedPercent);
    readonly failedPercent = computed(() => this.chartData().failedPercent);
    readonly tooltip = computed(() => {
        const data = this.chartData();
        return TestCasePassedBuildsChartComponent.generateTooltip(data.passedPercent, data.failedPercent, data.totalParticipations);
    });

    private static generateTooltip(passedPercent: number, failedPercent: number, totalStudents: number): string {
        const notExecutedPercent = round(100 - passedPercent - failedPercent);
        return `${passedPercent.toFixed(0)}% passed, ${failedPercent.toFixed(0)}% failed${
            notExecutedPercent > 0 ? `, ${notExecutedPercent}% not executed` : ''
        } of ${totalStudents} students.`;
    }
}
