import { Color } from '@swimlane/ngx-charts';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';

export interface ChartData {
    xScaleMax: number;
    scheme: Color;
    results: NgxChartsMultiSeriesDataEntry[];
}
