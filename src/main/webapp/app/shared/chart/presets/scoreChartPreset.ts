import { ChartComponent, ChartPreset } from 'app/shared/chart/chart.component';

export class ScoreChartPreset implements ChartPreset {
    private chart: ChartComponent;
    private pattern: CanvasPattern;

    constructor() {
        this.pattern = this.createPattern();
    }

    applyTo(chart: ChartComponent): void {
        this.chart = chart;
        chart.setType('horizontalBar');
        chart.setYAxe(0, { stacked: true }, false);
        chart.setXAxe(0, { stacked: true, ticks: { min: 0, stepSize: 25, callback: (value) => value + '%' } }, false);
        chart.setLegend(true);
    }

    setValues(positive: number, negative: number) {
        this.chart.updateDataset(0, {
            data: [positive - negative],
            backgroundColor: '#28a745',
        });
        this.chart.updateDataset(1, {
            data: [negative],
            backgroundColor: this.pattern,
        });
    }

    private createPattern() {
        const c = document.createElement('canvas');
        c.width = 10;
        c.height = 10;
        const ctx = c.getContext('2d')!;

        const x0 = 12;
        const y0 = -2;

        const x1 = -2;
        const y1 = 12;
        const offset = 10;

        ctx.fillStyle = '#28a745';
        ctx.fillRect(0, 0, 10, 10);

        ctx.strokeStyle = '#dc3545';
        ctx.lineWidth = 3.53;
        ctx.beginPath();
        ctx.moveTo(x0, y0);
        ctx.lineTo(x1, y1);
        ctx.moveTo(x0 - offset, y0);
        ctx.lineTo(x1 - offset, y1);
        ctx.moveTo(x0 + offset, y0);
        ctx.lineTo(x1 + offset, y1);
        ctx.stroke();

        return ctx.createPattern(c, 'repeat')!;
    }
}
