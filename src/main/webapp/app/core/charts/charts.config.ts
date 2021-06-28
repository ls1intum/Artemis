import ChartDataLabels from 'chartjs-plugin-datalabels';
import annotationPlugin from 'chartjs-plugin-annotation';
import Chart from 'chart.js/auto';

/**
 * Globally registering chart.js dependencies.
 * We also register the ChartDataLabels plugin globally but set display to false. This way, setting display to true
 * in specific chart options enables the datalabels plugin
 */
export function ChartConfig() {
    Chart.register(ChartDataLabels);
    Chart.register(annotationPlugin);
    Chart.defaults.plugins.datalabels!.display = false;
}
