import { Color, ScaleType } from '@swimlane/ngx-charts';
import * as shape from 'd3-shape';
import { NgxChartsEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { Subscription } from 'rxjs';
import { ExamActionService } from '../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import dayjs from 'dayjs/esm';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';

export abstract class ChartComponent {
    // Subscriptions
    protected routeSubscription?: Subscription;
    protected examActionSubscription?: Subscription;

    // Exam
    protected examId: number;
    protected courseId: number;

    // Actions
    filteredExamActions: ExamAction[] = [];

    chartIdentifierKey = '';

    showNumberLastTimeStamps = 10;
    timeStampGapInSeconds = 15;

    // Chart
    ngxData: NgxChartsEntry[] = [];
    ngxColor: Color;
    curve: any = shape.curveMonotoneX;
    legend: boolean;
    routerLink: any[];

    protected constructor(private route: ActivatedRoute, protected examActionService: ExamActionService, chartIdentifierKey: string, legend: boolean, colors?: string[]) {
        this.chartIdentifierKey = chartIdentifierKey;
        this.legend = legend;

        this.ngxColor = {
            name: this.chartIdentifierKey,
            selectable: true,
            group: ScaleType.Ordinal,
            domain: colors ?? [],
        };
    }

    /**
     * Inits all subscriptions.
     * @protected
     */
    protected initSubscriptions() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = Number(params['examId']);
            this.courseId = Number(params['courseId']);
        });

        this.examActionSubscription = this.examActionService.getExamMonitoringObservable(this.examId)?.subscribe((examActions: ExamAction[]) => {
            const t0 = performance.now();
            examActions.forEach((action) => {
                if (action && this.filterRenderedData(action)) {
                    this.evaluateAndAddAction(action);
                }
            });
            const t1 = performance.now();
            console.log(`${this.chartIdentifierKey}: Filter and add took ${t1 - t0} milliseconds.`);
        });
    }

    /**
     * Unsubscribes from all subscriptions.
     * @protected
     */
    protected endSubscriptions() {
        this.examActionSubscription?.unsubscribe();
    }

    protected initRenderRate(seconds: number) {
        // Trigger change detection every x seconds and filter the data
        setInterval(() => {
            this.updateData();
        }, seconds * 1000);
    }

    /**
     * Create and initialize the data for the chart.
     */
    initData(): void {
        this.filteredExamActions = (this.examActionService.cachedExamActions.get(this.examId) ?? []).filter((action) => this.filterRenderedData(action));
    }

    /**
     * Updates the data for the chart.
     */
    abstract updateData(): void;

    /**
     * The default case is that we don't filter any actions. This filter is adapted in subclasses.
     * @param examAction
     */
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    filterRenderedData(examAction: ExamAction): boolean {
        return true;
    }

    /**
     * The default case is that we don't evaluate the action in this place and simply add it. This evaluation is adapted in subclasses.
     * @param examAction
     */
    evaluateAndAddAction(examAction: ExamAction) {
        this.filteredExamActions.push(examAction);
    }

    /**
     * Method to filter actions which are not in the specified time frame.
     * @param examAction received action
     * @protected
     */
    protected filterActionsNotInTimeframe(examAction: ExamAction) {
        return ceilDayjsSeconds(dayjs(), this.timeStampGapInSeconds)
            .subtract(this.showNumberLastTimeStamps * this.timeStampGapInSeconds, 'seconds')
            .isBefore(examAction.ceiledTimestamp ?? examAction.timestamp);
    }

    /**
     * Method to get the last x time stamps.
     */
    public getLastXTimestamps() {
        const ceiledNow = ceilDayjsSeconds(dayjs(), 15);
        const timestamps = [];
        for (let i = this.showNumberLastTimeStamps - 1; i >= 0; i--) {
            timestamps.push(ceiledNow.subtract(i * this.timeStampGapInSeconds, 'seconds'));
        }
        return timestamps;
    }
}
