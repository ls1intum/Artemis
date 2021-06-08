import { Component, Input, ViewChild } from '@angular/core';
import { ChartDataset, ChartHoverOptions, ChartLayoutPaddingObject, ChartLegendOptions, ChartOptions, ChartTooltipOptions, ChartType, ChartXAxe, ChartYAxe } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';

export interface ChartPreset {
    applyTo(chart: ChartComponent): void;
}

@Component({
    selector: 'jhi-chart',
    template: `
        <div style="position: relative; width: 100%; height: 100%;">
            <canvas baseChart [datasets]="chartDatasets" [labels]="chartLabels" [options]="chartOptions" [chartType]="chartType"></canvas>
        </div>
    `,
})
export class ChartComponent {
    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    @Input() set preset(preset: ChartPreset) {
        preset.applyTo(this);
    }

    @Input() set type(type: ChartType) {
        this.chartType = type;
    }
    @Input() set datasets(datasets: ChartDataset[]) {
        this.chartDatasets = datasets;
    }
    @Input() set labels(labels: Label[]) {
        this.chartLabels = labels;
    }
    @Input() set options(options: ChartOptions) {
        this.chartOptions = options;
    }

    chartDatasets: ChartDataset[] = [];
    chartType: ChartType = 'bar';
    chartLabels: Label[] = [];
    chartOptions: ChartOptions = {
        plugins: {
            legend: {
                display: false,
            },
            title: {
                display: false,
            },
            tooltip: {
                enabled: false,
            },
        },
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
        scales: {
            y: {},
            x: {},
        },
    };

    setType(type: ChartType) {
        this.chartType = type;
    }

    private applyOptions(fn: (options: ChartOptions) => void, shouldUpdate = true) {
        if (this.chart) {
            fn(this.chart.options);
            if (shouldUpdate) {
                this.chart.update();
            }
        } else {
            fn(this.chartOptions);
        }
    }

    setPadding(padding: ChartLayoutPaddingObject, shouldUpdate = true) {
        this.applyOptions((options) => {
            Object.assign(options.layout!.padding, padding);
        }, shouldUpdate);
    }

    setLegend(legend: ChartLegendOptions | boolean, shouldUpdate = true) {
        this.applyOptions((options) => {
            if (typeof legend === 'boolean') {
                Object.assign(options.plugins?.legend, { display: legend });
            } else {
                Object.assign(options.plugins?.legend, { display: true }, legend);
            }
        }, shouldUpdate);
    }

    setTooltip(tooltip: ChartTooltipOptions | boolean, shouldUpdate = true) {
        this.applyOptions((options) => {
            if (typeof tooltip === 'boolean') {
                Object.assign(options.plugins?.tooltip, { enabled: tooltip });
            } else {
                Object.assign(options.plugins?.tooltip, { enabled: true }, tooltip);
            }
        }, shouldUpdate);
    }

    setHover(hover: ChartHoverOptions, shouldUpdate = true) {
        this.applyOptions((options) => {
            if (!options.hover) {
                options.hover = hover;
            } else {
                Object.assign(options.hover, hover);
            }
        }, shouldUpdate);
    }

    setYAxe(index: number, axe: ChartYAxe, shouldUpdate = true) {
        this.applyOptions((options) => {
            if (!options.scales!.y![index]) {
                options.scales!.y![index] = axe;
            } else {
                Object.assign(options.scales!.y![index], axe);
            }
        }, shouldUpdate);
    }

    setXAxe(index: number, axe: ChartXAxe, shouldUpdate = true) {
        this.applyOptions((options) => {
            if (!options.scales!.x![index]) {
                options.scales!.x![index] = axe;
            } else {
                Object.assign(options.scales!.x![index], axe);
            }
        }, shouldUpdate);
    }

    updateDataset(index: number, dataset: ChartDataset) {
        if (!this.chartDatasets[index]) {
            this.chartDatasets[index] = dataset;
        } else {
            Object.assign(this.chartDatasets[index], dataset);
        }
    }

    setLabels(labels: Label[]) {
        this.chartLabels = labels;
    }

    setBarChartToHorizontal(shouldUpdate: boolean) {
        this.applyOptions((options) => {
            Object.assign(options.indexAxis, 'y');
        }, shouldUpdate);
    }
}
