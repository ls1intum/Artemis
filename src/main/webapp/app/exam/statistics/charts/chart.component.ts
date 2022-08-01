import { Color, ScaleType } from '@swimlane/ngx-charts';
import * as shape from 'd3-shape';
import { NgxChartsEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { Subscription } from 'rxjs';
import { ExamActionService } from '../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import dayjs from 'dayjs/esm';
import { ceilDayjsSeconds } from 'app/exam/statistics/charts/live-statistics-chart';

export abstract class ChartComponent {
    // Subscriptions
    protected routeSubscription?: Subscription;

    // Exam
    protected examId: number;
    protected courseId: number;

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
     * Inits all subscriptions (without the exam action subscription).
     * @protected
     */
    protected initSubscriptions() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = Number(params['examId']);
            this.courseId = Number(params['courseId']);
        });
    }

    /**
     * Unsubscribes from all subscriptions.
     * @protected
     */
    protected endSubscriptions() {
        this.routeSubscription?.unsubscribe();
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
    abstract initData(): void;

    /**
     * Updates the data for the chart.
     */
    abstract updateData(): void;

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
