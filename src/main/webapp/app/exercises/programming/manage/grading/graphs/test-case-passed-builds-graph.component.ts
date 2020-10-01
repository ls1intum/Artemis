import { Component, Input, OnChanges } from '@angular/core';
import { TestCaseStats } from 'app/entities/programming-exercise-test-case-statistics.model';

@Component({
    selector: 'jhi-test-case-passed-builds-graph',
    template: `
        <div placement="left" [ngbTooltip]="passedPercent.toFixed(0) + '% passed, ' + failedPercent.toFixed(0) + '% failed.'">
            <svg viewBox="0 0 100 10" style="border-radius: 4px">
                <rect x="0" y="0" width="100" height="10" fill="#ddd"></rect>

                <rect x="0" y="0" [attr.width]="passedPercent" height="10" fill="#28a745"></rect>
                <rect [attr.x]="passedPercent" y="0" [attr.width]="failedPercent" height="10" fill="#dc3545"></rect>
            </svg>
        </div>
    `,
})
export class TestCasePassedBuildsGraphComponent implements OnChanges {
    @Input() testCaseStats?: TestCaseStats;
    @Input() totalParticipations: number;

    passedPercent: number;
    failedPercent: number;

    ngOnChanges(): void {
        const passedPercent = ((this.testCaseStats?.numPassed || 0) / this.totalParticipations) * 100;
        const failedPercent = ((this.testCaseStats?.numFailed || 0) / this.totalParticipations) * 100;

        setTimeout(() => {
            this.passedPercent = passedPercent;
            this.failedPercent = failedPercent;
        });
    }
}
