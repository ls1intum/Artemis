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

    /**
     * Formats the given duration to a string with two decimal places followed by 's'.
     * If the duration is undefined or null, returns '-'.
     *
     * @param duration the duration in seconds to format
     * @return the formatted duration string
     */
    private formatDuration(duration?: number): string {
        const DECIMAL_PLACES = 2;
        return duration ? `${duration.toFixed(DECIMAL_PLACES)}s` : '-';
    }

    agentSetupDuration = computed(() => this.formatDuration(this.detail().data.buildLogStatistics.agentSetupDuration));
    testDuration = computed(() => this.formatDuration(this.detail().data.buildLogStatistics.testDuration));
    scaDuration = computed(() => this.formatDuration(this.detail().data.buildLogStatistics.scaDuration));
    totalJobDuration = computed(() => this.formatDuration(this.detail().data.buildLogStatistics.totalJobDuration));
}
