import { ChartComponent, ChartPreset } from 'app/shared/chart/chart.component';
import { ChartData, registerables } from 'chart.js';
import { Label } from 'ng2-charts';
import Chart from 'chart.js/auto';
import ChartDataLabels from 'chartjs-plugin-datalabels';
import annotationPlugin from 'chartjs-plugin-annotation';

Chart.register(...registerables);
Chart.register(ChartDataLabels);
Chart.register(annotationPlugin);
Chart.defaults.plugins.datalabels!.display = false;

export class HorizontalStackedBarChartPreset implements ChartPreset {
    private readonly labels: Label[];
    private readonly totalText: string[];

    constructor(labels: Label[], totalText: string[]) {
        this.labels = labels;
        this.totalText = totalText;
    }

    applyTo(chart: ChartComponent): void {
        const preset = this;

        chart.setType('bar');
        chart.setLabels(this.labels);
        chart.setYAxe(0, { stacked: true }, false);
        chart.setXAxe(0, { stacked: true, ticks: { min: 0, stepSize: 25, callback: (value: string) => value + '%' } }, false);
        chart.setHover({ mode: 'dataset' });
        chart.setBarChartToHorizontal(false);
        chart.setTooltip(
            {
                mode: 'dataset',
                position: 'nearest',
                callbacks: {
                    title(items: Chart.ChartTooltipItem[], data: ChartData) {
                        return data.datasets![items[0].datasetIndex!].label!;
                    },
                    label(item: Chart.ChartTooltipItem, data: ChartData) {
                        return item.yLabel + ': ' + (data.datasets![item.datasetIndex!].data![item.index!] as number).toFixed(2) + '% of ' + preset.totalText[item.index!] + '.';
                    },
                },
            },
            false,
        );
    }
}
