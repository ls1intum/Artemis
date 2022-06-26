export interface NgxChartsSingleSeriesDataEntry extends NgxChartsEntry {
    name: string;
    value: number;
}

export interface NgxChartsMultiSeriesDataEntry extends NgxChartsEntry {
    name: string;
    series: any[];
}

export interface NgxChartsEntry {}
