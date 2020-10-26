import { ChartComponent, ChartPreset } from 'app/shared/chart/chart.component';
import { ChartDataSets, ChartLegendLabelItem } from 'chart.js';
import { Exercise } from 'app/entities/exercise.model';

export class ScoreChartPreset implements ChartPreset {
    private chart: ChartComponent;
    private datasets: ChartDataSets[] = [];

    private readonly redGreenPattern: CanvasPattern;
    private readonly redTransparentPattern: CanvasPattern;
    private readonly labels: string[];

    constructor(labels: string[]) {
        this.labels = labels;
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
                            text: this.labels[0],
                            fillStyle: '#28a745',
                            strokeStyle: '#28a745',
                            lineWidth: 1,
                        },
                        {
                            text: this.labels[1],
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
     * @param positive Sum of positive credits of the score
     * @param negative Sum of negative credits of the score
     * @param exercise The active exercise
     */
    setValues(positive: number, negative: number, exercise: Exercise) {
        const maxScoreWithBonus = exercise.maxScore! + (exercise.bonusPoints || 0);
        const maxScore = exercise.maxScore!;

        // cap to min and max values while maintaining correct negative points
        if (positive - negative > maxScoreWithBonus) {
            positive = maxScoreWithBonus;
            negative = 0;
        } else if (positive > maxScoreWithBonus) {
            negative -= positive - maxScoreWithBonus;
            positive = maxScoreWithBonus;
        } else if (positive - negative < 0) {
            negative = positive;
        }

        this.datasets = [
            {
                data: [((positive - negative) / maxScore) * 100],
                backgroundColor: '#28a745',
                hoverBackgroundColor: '#28a745',
            },
            {
                data: [(negative / maxScore) * 100],
                backgroundColor: this.redGreenPattern,
                hoverBackgroundColor: this.redGreenPattern,
            },
        ];
        if (this.chart) {
            this.chart.chartDatasets = this.datasets;
        }
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
