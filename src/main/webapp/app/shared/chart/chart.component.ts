import { Component, Input, ViewChild } from '@angular/core';
import { ChartDataSets, ChartLayoutPaddingObject, ChartLegendOptions, ChartOptions, ChartType, ChartXAxe, ChartYAxe } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';

@Component({
    selector: 'jhi-chart',
    template: `
        <div class="chartWrapper">
            <canvas baseChart [height]="height" [datasets]="[]" [labels]="[]" [options]="options" [chartType]="type"></canvas>
        </div>
    `,
})
export class ChartComponent {
    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    @Input() height = 400;
    @Input() type: ChartType;

    public options: ChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        layout: {
            padding: {
                left: 0,
                right: 0,
                top: 0,
                bottom: 0,
            },
        },
        legend: {
            display: false,
        },
        title: {
            display: false,
        },
        tooltips: {
            enabled: true,
        },
        scales: {
            yAxes: [],
            xAxes: [],
        },
    };

    setPadding(padding: ChartLayoutPaddingObject) {
        if (!this.chart) {
            setTimeout(() => this.setPadding(padding));
            return;
        }
        Object.assign(this.chart.options.layout!.padding, padding);
        this.chart.update();
    }

    setLegend(legend: ChartLegendOptions | boolean) {
        if (typeof legend === 'boolean') {
            Object.assign(this.chart.options.legend, { display: legend });
        } else {
            Object.assign(this.chart.options.legend, { display: true }, legend);
        }
        this.chart.update();
    }

    setYAxe(index: number, axe: ChartYAxe) {
        if (!this.chart.options.scales!.yAxes![index]) {
            this.chart.options.scales!.yAxes![index] = axe;
        } else {
            Object.assign(this.chart.options.scales!.yAxes![index], axe);
        }
        this.chart.update();
    }

    setXAxe(index: number, axe: ChartXAxe) {
        if (!this.chart.options.scales!.xAxes![index]) {
            this.chart.options.scales!.xAxes![index] = axe;
        } else {
            Object.assign(this.chart.options.scales!.xAxes![index], axe);
        }
        this.chart.update();
    }

    updateDataset(index: number, dataset: ChartDataSets) {
        if (!this.chart.datasets[index]) {
            this.chart.datasets[index] = dataset;
        } else {
            Object.assign(this.chart.datasets[index], dataset);
        }
        this.chart.update();
    }

    setLabels(labels: Label[]) {
        this.chart.labels = labels;
        this.chart.update();
    }
}
