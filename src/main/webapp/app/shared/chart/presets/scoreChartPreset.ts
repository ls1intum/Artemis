import { ChartComponent, ChartPreset } from 'app/shared/chart/chart.component';
import { ChartDataSets, ChartLegendLabelItem } from 'chart.js';

export class ScoreChartPreset implements ChartPreset {
    private chart: ChartComponent;
    private datasets: ChartDataSets[] = [];

    private readonly redGreenPattern: CanvasPattern;
    private readonly redTransparentPattern: CanvasPattern;
    private readonly labels: string[];
    private valueLabels: string[];
    private showValues: boolean;

    constructor(labels: string[], showValues = true) {
        this.labels = labels;
        this.valueLabels = ['', ''];
        this.showValues = showValues;
        this.redGreenPattern = this.createPattern('#28a745');
        this.redTransparentPattern = this.createPattern('transparent');
    }

    applyTo(chart: ChartComponent): void {
        this.chart = chart;
        chart.setType('horizontalBar');
        chart.setYAxe(0, { stacked: true }, false);
        chart.setXAxe(0, { stacked: true, ticks: { min: 0, stepSize: 25, suggestedMax: 100, callback: (value) => value + '%' } }, false);
        chart.setLegend({
            position: 'bottom',
            labels: {
                generateLabels: () =>
                    [
                        {
                            datasetIndex: 0,
                            text: this.labels[0] + (this.showValues ? `: ${this.valueLabels[0]}` : ''),
                            fillStyle: '#28a745',
                            strokeStyle: '#28a745',
                            lineWidth: 1,
                        },
                        {
                            datasetIndex: 1,
                            text: this.labels[1] + (this.showValues ? `: ${this.valueLabels[1]}` : ''),
                            fillStyle: this.redTransparentPattern,
                            strokeStyle: '#dc3545',
                            lineWidth: 1,
                        },
                    ] as ChartLegendLabelItem[],
            },
        });
        chart.chartDatasets = this.datasets;
    }

    /**
     * Updates the datasets of the charts with the correct values and colors.
     * @param receivedPositive Sum of positive credits of the score
     * @param appliedNegative Sum of applied negative credits
     * @param receivedNegative Sum of received negative credits
     * @param maxScore The relevant maximal points of the exercise
     * @param maxScoreWithBonus The actual received points + optional bonus points
     */
    setValues(receivedPositive: number, appliedNegative: number, receivedNegative: number, maxScore: number, maxScoreWithBonus: number) {
        let appliedPositive = receivedPositive;

        // cap to min and max values while maintaining correct negative points
        if (appliedPositive - appliedNegative > maxScoreWithBonus) {
            appliedPositive = maxScoreWithBonus;
            appliedNegative = 0;
        } else if (appliedPositive > maxScoreWithBonus) {
            appliedNegative -= appliedPositive - maxScoreWithBonus;
            appliedPositive = maxScoreWithBonus;
        } else if (appliedPositive - appliedNegative < 0) {
            appliedNegative = appliedPositive;
        }

        this.valueLabels[0] = this.roundToDecimals(appliedPositive, 1) + (appliedPositive !== receivedPositive ? ` of ${this.roundToDecimals(receivedPositive, 1)}` : '');
        this.valueLabels[1] = this.roundToDecimals(appliedNegative, 1) + (appliedNegative !== receivedNegative ? ` of ${this.roundToDecimals(receivedNegative, 1)}` : '');

        this.datasets = [
            {
                data: [this.roundToDecimals(((appliedPositive - appliedNegative) / maxScore) * 100, 2)],
                backgroundColor: '#28a745',
                hoverBackgroundColor: '#28a745',
            },
            {
                data: [this.roundToDecimals((appliedNegative / maxScore) * 100, 2)],
                backgroundColor: this.redGreenPattern,
                hoverBackgroundColor: this.redGreenPattern,
            },
        ];
        if (this.chart) {
            this.chart.chartDatasets = this.datasets;
        }
    }

    // TODO: rene: use same round function
    private roundToDecimals(i: number, n: number) {
        const f = 10 ** n;
        return Math.round(i * f) / f;
    }

    /**
     * Generates a diagonal red stripes pattern used for the deductions bar.
     * @param fillStyle The background of the pattern
     * @private
     */
    private createPattern(fillStyle: string) {
        const c = document.createElement('canvas');
        c.width = 10;
        c.height = 10;
        const ctx = c.getContext('2d')!;

        const x0 = 12;
        const y0 = -2;

        const x1 = -2;
        const y1 = 12;
        const offset = 10;

        ctx.fillStyle = fillStyle;
        ctx.fillRect(0, 0, 10, 10);

        ctx.strokeStyle = '#dc3545';
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.moveTo(x0, y0);
        ctx.lineTo(x1, y1);
        ctx.moveTo(x0 - offset, y0);
        ctx.lineTo(x1 - offset, y1);
        ctx.moveTo(x0 + offset, y0);
        ctx.lineTo(x1 + offset, y1);
        ctx.stroke();

        return ctx.createPattern(c, 'repeat')!;
    }
}
