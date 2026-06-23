import { Component, computed, inject, signal } from '@angular/core';
import ChartDataLabels from 'chartjs-plugin-datalabels';
import { round } from 'app/foundation/util/utils';
import { QuizStatistic } from 'app/quiz/shared/entities/quiz-statistic.model';
import { TranslateService } from '@ngx-translate/core';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { singleSeriesChartData } from 'app/shared-ui/chart/chart-adapters';
import { barChartOptions } from 'app/shared-ui/chart/chart-options';

@Component({
    template: '',
})
export abstract class AbstractQuizStatisticComponent {
    protected translateService = inject(TranslateService);
    protected chartColorService = inject(ChartColorService);

    data: number[] = [];
    ratedData: number[] = [];
    unratedData: number[] = [];
    rated = true;
    participants: number;

    chartLabels: string[] = [];
    totalParticipants = 0;

    /** The current bar entries; updated via {@link updateChartData}. */
    protected chartEntries = signal<ChartSeriesEntry[]>([]);
    /** The per-bar colors; quiz statistics use literal hex colors. */
    protected chartColors = signal<string[]>([]);
    protected maxScale = signal<number | undefined>(undefined);
    protected xAxisLabel = signal('');
    protected yAxisLabel = signal('');

    private resolvedChartColors = this.chartColorService.resolvedColors(() => this.chartColors());

    readonly chartData = computed(() => singleSeriesChartData(this.chartEntries(), this.resolvedChartColors()));
    readonly chartOptions = computed(() =>
        barChartOptions({
            xAxis: { label: this.xAxisLabel() },
            yAxis: { label: this.yAxisLabel(), max: this.maxScale() },
            tooltip: false,
            dataLabels: { formatter: (value) => this.formatDataLabel(value) },
        }),
    );
    /** chartjs-plugin-datalabels renders the persistent per-bar value labels; pass to <p-chart [plugins]>. */
    readonly dataLabelsPlugin = [ChartDataLabels];

    /**
     * Depending on if the rated or unrated results should be displayed,
     * The amount of participants as well as the corresponding scores are set
     * @param statistic the statistic containing amount of participation
     */
    protected setData(statistic: QuizStatistic): void {
        if (this.rated) {
            this.participants = statistic.participantsRated!;
            // if rated is true use the rated Data and add the rated CorrectCounter
            this.data = [];
            this.data = [...this.ratedData];
        } else {
            this.participants = statistic.participantsUnrated!;
            // if rated is false use the unrated Data and add the unrated CorrectCounter
            this.data = [];
            this.data = [...this.unratedData];
        }
    }

    /**
     * Publishes the current data and labels to the chart and calculates the maximum value
     * on the y-axis in order to ensure a shapely display.
     */
    protected updateChartData(): void {
        this.chartEntries.set(this.data.map((score, index) => ({ name: this.chartLabels[index], value: score })));
        this.maxScale.set(this.calculateHeightOfChartData(this.data));
    }

    /**
     * switch between showing and hiding the solution in the chart
     *  1. change the amount of  participants
     *  2. change the bar-Data
     */
    switchRated(): void {
        this.rated = !this.rated;
        this.loadDataInDiagram();
    }

    /**
     * Modifies the datalabel for each bar to the following pattern:
     * absolute value (absolute value/amount of participants)
     * @param absoluteValue the absolute value represented by the corresponding bar
     * @returns string of the following pattern: absolute value (relative value)
     */
    protected formatDataLabel(absoluteValue: number): string {
        if (!this.totalParticipants || !this.participants) {
            return absoluteValue + ' (0%)';
        }
        return absoluteValue + ' (' + round((absoluteValue / this.participants) * 100, 1) + '%)';
    }

    /**
     * Calculates the maximum value on the y-axis on a chart depending on the data to display
     * @param data the array of data that is to display by the chart
     * @returns height of the chart
     */
    private calculateHeightOfChartData(data: number[]): number {
        const max = Math.max(...data);
        // we provide 300 as buffer at the top to display labels
        const height = Math.ceil((max + 1) / 10) * 10;
        if (height < 10) {
            return height + 3;
        } else if (height < 1000) {
            // add 25%, round to the next 10
            return Math.ceil(height * 0.125) * 10;
        } else {
            // add 25%, round to the next 100
            return Math.ceil(height * 0.0125) * 100;
        }
    }

    /**
     * Sets the axis labels given the translation paths
     * @param xAxisLabel translation path for x-axis label
     * @param yAxisLabel translation path for y-axis label
     */
    setAxisLabels(xAxisLabel: string, yAxisLabel: string): void {
        this.xAxisLabel.set(this.translateService.instant(xAxisLabel));
        this.yAxisLabel.set(this.translateService.instant(yAxisLabel));
    }

    /**
     * check if the rated or unrated
     * load the rated or unrated data into the diagram
     */
    abstract loadDataInDiagram(): void;
}
