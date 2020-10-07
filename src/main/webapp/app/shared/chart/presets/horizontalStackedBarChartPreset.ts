import { ChartComponent, ChartPreset } from 'app/shared/chart/chart.component';
import { ChartData, ChartTooltipItem } from 'chart.js';
import { Label } from 'ng2-charts';

export class HorizontalStackedBarChartPreset implements ChartPreset {
    private readonly labels: Label[];

    constructor(labels: Label[]) {
        this.labels = labels;
    }

    applyTo(chart: ChartComponent): void {
        chart.setType('horizontalBar');
        chart.setLabels(this.labels);
        chart.setYAxe(0, { stacked: true }, false);
        chart.setXAxe(0, { stacked: true }, false);
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
                        return item.yLabel + ': ' + (data.datasets![item.datasetIndex!].data![item.index!] as number).toFixed(2) + '%';
                    },
                },
            },
            false,
        );
    }
}
