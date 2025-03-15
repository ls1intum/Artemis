import { Component, computed, input } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingBuildStatisticsDetail } from 'app/detail-overview-list/detail.model';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-programming-build-statistics',
    imports: [TranslateDirective, CommonModule],
    templateUrl: './programming-build-statistics.component.html',
})
export class ProgrammingBuildStatisticsComponent {
    detail = input.required<ProgrammingBuildStatisticsDetail>();

    agentSetupDuration = computed(() => {
        const duration = this.detail().data.buildLogStatistics.agentSetupDuration;
        return duration ? `${duration.toFixed(2)}s` : '-';
    });

    testDuration = computed(() => {
        const duration = this.detail().data.buildLogStatistics.testDuration;
        return duration ? `${duration.toFixed(2)}s` : '-';
    });

    scaDuration = computed(() => {
        const duration = this.detail().data.buildLogStatistics.scaDuration;
        return duration ? `${duration.toFixed(2)}s` : '-';
    });

    totalJobDuration = computed(() => {
        const duration = this.detail().data.buildLogStatistics.totalJobDuration;
        return duration ? `${duration.toFixed(2)}s` : '-';
    });
}
