import { NgModule } from '@angular/core';
import { registerables } from 'chart.js';
import Chart from 'chart.js/auto';
import ChartDataLabels from 'chartjs-plugin-datalabels';
import annotationPlugin from 'chartjs-plugin-annotation';

/*
    Globally registering chart.js dependencies.
    We also register the ChartDataLabels plugin globally but set display to false. This way, setting display to true
    in specific chart options enables the datalabels plugin
 */
Chart.register(...registerables);
Chart.register(ChartDataLabels);
Chart.register(annotationPlugin);
Chart.defaults.plugins.datalabels!.display = false;

@NgModule({
    imports: [],
    declarations: [],
    providers: [],
    exports: [],
})
export class ArtemisChartsModule {}
