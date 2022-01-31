// First draft in order to make the typing of the chart creation a bit more readable

export interface NgxChartsSingleSeriesDataEntry {
    name: string;
    value: number;
}

export interface NgxChartsMultiSeriesDataEntry {
    name: string;
    series: any[];
}
