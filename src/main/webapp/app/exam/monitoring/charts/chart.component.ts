import { Color, ScaleType } from '@swimlane/ngx-charts';
import * as shape from 'd3-shape';
import { NgxChartsEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { Subscription } from 'rxjs';
import { ExamMonitoringWebsocketService } from '../exam-monitoring-websocket.service';
import { ActivatedRoute } from '@angular/router';
import { ExamAction } from '../../../entities/exam-user-activity.model';

export abstract class ChartComponent {
    // Subscriptions
    protected routeSubscription?: Subscription;
    protected examActionSubscription?: Subscription;

    // Exam
    protected examId: number;
    protected courseId: number;

    // Actions
    protected receivedExamActions: ExamAction[] = [];
    protected filteredExamActions: ExamAction[] = [];

    chartIdentifierKey = '';

    // Chart
    ngxData: NgxChartsEntry[] = [];
    ngxColor: Color;
    curve: any = shape.curveMonotoneX;
    legend: boolean;
    routerLink: any[];

    protected constructor(
        private route: ActivatedRoute,
        private examMonitoringWebsocketService: ExamMonitoringWebsocketService,
        chartIdentifierKey: string,
        legend: boolean,
        colors?: string[],
    ) {
        this.chartIdentifierKey = chartIdentifierKey;
        this.legend = legend;

        this.ngxColor = {
            name: this.chartIdentifierKey,
            selectable: true,
            group: ScaleType.Ordinal,
            domain: colors ?? [],
        };
    }

    protected initSubscriptions() {
        this.routeSubscription = this.route.parent?.params.subscribe((params) => {
            this.examId = parseInt(params['examId'], 10);
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.examActionSubscription = this.examMonitoringWebsocketService.getExamMonitoringObservable(this.examId)?.subscribe((examAction) => {
            if (examAction) {
                this.receivedExamActions.push(examAction);
                this.filteredExamActions.push(examAction);
                console.log(this.filteredExamActions);
            }
        });
    }

    protected endSubscriptions() {
        this.examActionSubscription?.unsubscribe();
    }

    protected initRenderRate(seconds: number) {
        // Trigger change detection every x seconds and filter the data
        setInterval(() => {
            this.filteredExamActions = [...this.filteredExamActions.filter(this.filterRenderedData)];
            this.initData();
        }, seconds * 1000);
    }

    /**
     * Create and initialize the data for the chart.
     */
    abstract initData(): void;

    abstract filterRenderedData(examAction: ExamAction): boolean;
}
