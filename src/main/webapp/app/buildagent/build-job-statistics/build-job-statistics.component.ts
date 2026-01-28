import { ChangeDetectionStrategy, Component, OnInit, effect, inject, input, signal } from '@angular/core';
import { BuildJobStatistics, SpanType } from 'app/buildagent/shared/entities/build-job.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { onError } from 'app/shared/util/global.utils';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { Color, NgxChartsModule, ScaleType } from '@swimlane/ngx-charts';
import { BuildOverviewService } from 'app/buildagent/build-queue/build-overview.service';
import { ActivatedRoute } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { take } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';

/**
 * Component that displays build job statistics with a pie chart visualization.
 * Shows the distribution of build job results: successful, failed, cancelled, timeout, and missing.
 *
 * Can receive statistics via input signal (for embedding in other components like BuildAgentDetailsComponent)
 * or fetch them from the REST API based on the current route (for the build queue view).
 *
 * Supports time span filtering (day, week, month) in build queue mode.
 *
 * Uses OnPush change detection with signals for optimal performance.
 */
@Component({
    selector: 'jhi-build-job-statistics',
    imports: [TranslateDirective, NgxChartsModule, HelpIconComponent],
    templateUrl: './build-job-statistics.component.html',
    styleUrl: './build-job-statistics.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BuildJobStatisticsComponent implements OnInit {
    private buildQueueService = inject(BuildOverviewService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);

    /**
     * Optional input signal for providing statistics directly from a parent component.
     * When provided, the component uses these statistics instead of fetching from the API.
     */
    buildJobStatisticsInput = input<BuildJobStatistics>();

    protected readonly SpanType = SpanType;

    /** Currently selected time span for statistics (day, week, or month) */
    currentSpan: SpanType = SpanType.WEEK;

    /** Formatted percentage strings for display */
    successfulBuildsPercentage = signal('-%');
    failedBuildsPercentage = signal('-%');
    cancelledBuildsPercentage = signal('-%');
    timeoutBuildsPercentage = signal('-%');
    missingBuildsPercentage = signal('-%');

    /** Whether to show the time span selector tabs (hidden when statistics come from input) */
    displaySpanSelector = true;

    /** Whether to show missing builds in the chart (hidden when embedded in agent details) */
    displayMissingBuilds = true;

    /** Current build job statistics data */
    buildJobStatistics = signal<BuildJobStatistics>(new BuildJobStatistics());

    constructor() {
        effect(() => {
            if (this.buildJobStatisticsInput()) {
                this.updateDisplayedBuildJobStatistics(this.buildJobStatisticsInput()!);
            }
        });
    }

    ngOnInit() {
        this.getBuildJobStatistics(this.currentSpan);
    }

    /** Data array for the pie chart visualization (ngx-charts format) */
    pieChartData = signal<NgxChartsSingleSeriesDataEntry[]>([]);

    /** Color scheme configuration for the pie chart segments */
    pieChartColorScheme = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        // Colors: green (success), red (failed), yellow (cancelled), blue (timeout), grey (missing)
        domain: [GraphColors.GREEN, GraphColors.RED, GraphColors.YELLOW, GraphColors.BLUE, GraphColors.GREY],
    } as Color;

    /**
     * Determines the context and fetches appropriate statistics.
     * In build queue context, fetches from API. Otherwise, uses input statistics.
     * @param span The time span for filtering statistics
     */
    getBuildJobStatistics(span: SpanType) {
        // Determine context by checking the current route
        this.route.url.pipe(take(1)).subscribe((urlSegments) => {
            const firstSegment = urlSegments[0];
            if (firstSegment?.path === 'build-overview') {
                // Build overview context: fetch statistics from API with time span filtering
                this.getBuildJobStatisticsForBuildQueue(span);
            } else {
                // Embedded in another component: use input statistics without span selector
                this.displayMissingBuilds = false;
                this.displaySpanSelector = false;
                this.updateDisplayedBuildJobStatistics(this.buildJobStatisticsInput()!);
            }
        });
    }

    /**
     * Fetches build job statistics from the API for the build queue view.
     * Uses course-specific or global endpoint depending on route context.
     * @param span The time span for filtering statistics (day, week, or month)
     */
    getBuildJobStatisticsForBuildQueue(span: SpanType = SpanType.WEEK) {
        this.route.paramMap.pipe(take(1)).subscribe((routeParams) => {
            const courseId = Number(routeParams.get('courseId'));
            if (courseId) {
                // Course mode: fetch statistics for this specific course
                this.buildQueueService.getBuildJobStatisticsForCourse(courseId, span).subscribe({
                    next: (statistics: BuildJobStatistics) => {
                        this.updateDisplayedBuildJobStatistics(statistics);
                    },
                    error: (errorResponse: HttpErrorResponse) => {
                        onError(this.alertService, errorResponse);
                    },
                });
            } else {
                // Admin mode: fetch global statistics across all courses
                this.buildQueueService.getBuildJobStatistics(span).subscribe({
                    next: (statistics: BuildJobStatistics) => {
                        this.updateDisplayedBuildJobStatistics(statistics);
                    },
                    error: (errorResponse: HttpErrorResponse) => {
                        onError(this.alertService, errorResponse);
                    },
                });
            }
        });
    }

    /**
     * Updates all displayed statistics including percentages and pie chart data.
     * Handles the edge case of zero total builds by showing placeholder dashes.
     * @param statistics The new build job statistics to display
     */
    updateDisplayedBuildJobStatistics(statistics: BuildJobStatistics) {
        this.buildJobStatistics.set(statistics);

        // Calculate percentages, using placeholder for zero builds case
        if (statistics.totalBuilds === 0) {
            this.successfulBuildsPercentage.set('-%');
            this.failedBuildsPercentage.set('-%');
            this.cancelledBuildsPercentage.set('-%');
            this.timeoutBuildsPercentage.set('-%');
            this.missingBuildsPercentage.set('-%');
        } else {
            const totalBuilds = statistics.totalBuilds;
            this.successfulBuildsPercentage.set(((statistics.successfulBuilds / totalBuilds) * 100).toFixed(2) + '%');
            this.failedBuildsPercentage.set(((statistics.failedBuilds / totalBuilds) * 100).toFixed(2) + '%');
            this.cancelledBuildsPercentage.set(((statistics.cancelledBuilds / totalBuilds) * 100).toFixed(2) + '%');
            this.timeoutBuildsPercentage.set(((statistics.timeOutBuilds / totalBuilds) * 100).toFixed(2) + '%');
            this.missingBuildsPercentage.set(((statistics.missingBuilds / totalBuilds) * 100).toFixed(2) + '%');
        }

        // Update pie chart data with current statistics
        this.pieChartData.set([
            { name: 'Successful', value: statistics.successfulBuilds },
            { name: 'Failed', value: statistics.failedBuilds },
            { name: 'Cancelled', value: statistics.cancelledBuilds },
            { name: 'Timeout', value: statistics.timeOutBuilds },
            { name: 'Missing', value: statistics.missingBuilds },
        ]);
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
