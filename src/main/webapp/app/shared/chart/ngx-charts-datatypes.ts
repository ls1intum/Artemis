export interface NgxChartsSingleSeriesDataEntry extends NgxChartsEntry {
    name: string;
    value: number;
}

export interface NgxChartsMultiSeriesDataEntry extends NgxChartsEntry {
    name: string;
    series: any[];
}

// eslint-disable-next-line @typescript-eslint/no-empty-interface
export interface NgxChartsEntry {}
