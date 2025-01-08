import { Component, OnInit, effect, inject, input } from '@angular/core';
import { BuildJobStatistics, SpanType } from 'app/entities/programming/build-job.model';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faAngleDown, faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { onError } from 'app/shared/util/global.utils';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/entities/statistics.model';
import { Color, NgxChartsModule, ScaleType } from '@swimlane/ngx-charts';
import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { ArtemisChartsModule } from 'app/shared/chart/artemis-charts.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-build-job-statistics',
    standalone: true,
    imports: [ArtemisSharedComponentModule, TranslateDirective, ArtemisChartsModule, ArtemisSharedModule, NgxChartsModule],
    templateUrl: './build-job-statistics.component.html',
    styleUrl: './build-job-statistics.component.scss',
})
export class BuildJobStatisticsComponent implements OnInit {
    private buildQueueService = inject(BuildQueueService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);

    buildJobStatisticsInput = input<BuildJobStatistics>();

    readonly faAngleDown = faAngleDown;
    readonly faAngleRight = faAngleRight;

    protected readonly SpanType = SpanType;
    currentSpan: SpanType = SpanType.WEEK;

    isCollapsed = false;
    successfulBuildsPercentage: string;
    failedBuildsPercentage: string;
    cancelledBuildsPercentage: string;
    timeoutBuildsPercentage: string;
    missingBuildsPercentage: string;

    displaySpanSelector = true;
    displayMissingBuilds = true;

    buildJobStatistics = new BuildJobStatistics();

    constructor() {
        effect(() => {
            this.getBuildJobStatistics(this.currentSpan);
        });
    }

    ngOnInit() {
        this.getBuildJobStatistics(this.currentSpan);
    }

    ngxData: NgxChartsSingleSeriesDataEntry[] = [];

    ngxColor = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.GREEN, GraphColors.RED, GraphColors.YELLOW, GraphColors.BLUE, GraphColors.GREY],
    } as Color;

    getBuildJobStatistics(span: SpanType) {
        this.route.url.pipe(take(1)).subscribe((url) => {
            if (url[0].path === 'build-queue') {
                this.getBuildJobStatisticsForBuildQueue(span);
            } else {
                console.log('statistics', this.buildJobStatisticsInput());
                this.displayMissingBuilds = false;
                this.displaySpanSelector = false;
                this.updateDisplayedBuildJobStatistics(this.buildJobStatisticsInput()!);
            }
        });
    }

    /**
     * Get Build Job Result statistics in the build queue view
     */
    getBuildJobStatisticsForBuildQueue(span: SpanType = SpanType.WEEK) {
        this.route.paramMap.pipe(take(1)).subscribe((params) => {
            const courseId = Number(params.get('courseId'));
            if (courseId) {
                this.buildQueueService.getBuildJobStatisticsForCourse(courseId, span).subscribe({
                    next: (res: BuildJobStatistics) => {
                        this.updateDisplayedBuildJobStatistics(res);
                    },
                    error: (res: HttpErrorResponse) => {
                        onError(this.alertService, res);
                    },
                });
            } else {
                this.buildQueueService.getBuildJobStatistics(span).subscribe({
                    next: (res: BuildJobStatistics) => {
                        this.updateDisplayedBuildJobStatistics(res);
                    },
                    error: (res: HttpErrorResponse) => {
                        onError(this.alertService, res);
                    },
                });
            }
        });
    }

    /**
     * Update the displayed build job statistics
     * @param stats The new build job statistics
     */
    updateDisplayedBuildJobStatistics(stats: BuildJobStatistics) {
        this.buildJobStatistics = stats;
        if (stats.totalBuilds === 0) {
            this.successfulBuildsPercentage = '-%';
            this.failedBuildsPercentage = '-%';
            this.cancelledBuildsPercentage = '-%';
            this.timeoutBuildsPercentage = '-%';
            this.missingBuildsPercentage = '-%';
        } else {
            this.successfulBuildsPercentage = ((stats.successfulBuilds / stats.totalBuilds) * 100).toFixed(2) + '%';
            this.failedBuildsPercentage = ((stats.failedBuilds / stats.totalBuilds) * 100).toFixed(2) + '%';
            this.cancelledBuildsPercentage = ((stats.cancelledBuilds / stats.totalBuilds) * 100).toFixed(2) + '%';
            this.timeoutBuildsPercentage = ((stats.timeOutBuilds / stats.totalBuilds) * 100).toFixed(2) + '%';
            this.missingBuildsPercentage = ((stats.missingBuilds / stats.totalBuilds) * 100).toFixed(2) + '%';
        }
        this.ngxData = [
            { name: 'Successful', value: stats.successfulBuilds },
            { name: 'Failed', value: stats.failedBuilds },
            { name: 'Cancelled', value: stats.cancelledBuilds },
            { name: 'Timeout', value: stats.timeOutBuilds },
            { name: 'Missing', value: stats.missingBuilds },
        ];
    }

    /**
     * Callback function when the tab is changed
     * @param span The new span
     */
    onTabChange(span: SpanType): void {
        if (this.currentSpan !== span) {
            this.currentSpan = span;
            this.getBuildJobStatistics(span);
        }
    }
}
