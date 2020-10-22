import { ChartComponent, ChartPreset } from 'app/shared/chart/chart.component';
import { ChartData, ChartTooltipItem } from 'chart.js';
import { Label } from 'ng2-charts';

export class HorizontalStackedBarChartPreset implements ChartPreset {
    private readonly labels: Label[];
    private readonly totalText: string[];

    constructor(labels: Label[], totalText: string[]) {
        this.labels = labels;
        this.totalText = totalText;
    }

    applyTo(chart: ChartComponent): void {
        const preset = this;

        chart.setType('horizontalBar');
        chart.setLabels(this.labels);
        chart.setYAxe(0, { stacked: true }, false);
        chart.setXAxe(0, { stacked: true, ticks: { min: 0, stepSize: 25, callback: (value) => value + '%' } }, false);
        chart.setHover({ mode: 'dataset' });
        chart.setTooltip(
            {
                mode: 'dataset',
                position: 'nearest',
                callbacks: {
                    title(items: ChartTooltipItem[], data: ChartData) {
                        return data.datasets![items[0].datasetIndex!].label!;
                    },
                    label(item: ChartTooltipItem, data: ChartData) {
                        return (
                            item.yLabel + ': ' + (data.datasets![item.datasetIndex!].data![item.index!] as number).toFixed(2) + '% of ' + preset.totalText[item.datasetIndex!] + '.'
                        );
                    },
                },
            },
            false,
        );
    }
}
