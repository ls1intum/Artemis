import { Component, Input, OnInit } from '@angular/core';
import { CourseStatisticsDataSet } from 'app/overview/course-statistics/course-statistics.component';

@Component({
    selector: 'jhi-doughnut-chart',
    templateUrl: './doughnut-chart.component.html',
    styleUrls: ['./doughnut-chart.component.scss'],
})
export class DoughnutChartComponent implements OnInit {
    @Input() doughnutChartTitle: string;

    @Input() currentPercentage: number;
    @Input() currentAbsolute: number;
    @Input() currentMax: number;

    stats: number[];

    // Chart.js data
    doughnutChartType = 'doughnut';
    doughnutChartColors = ['rgba(122, 204, 69, 1)', 'rgba(219, 0, 0, 1)'];
    doughnutChartLabels: string[] = ['Done', 'Not Done'];
    totalScoreOptions: object = {
        cutoutPercentage: 75,
        scaleShowVerticalLines: false,
        responsive: false,
        tooltips: {
            backgroundColor: 'rgba(0, 0, 0, 1)',
            callbacks: {
                label(tooltipItem: any, data: any) {
                    const value = data['datasets'][0]['data'][tooltipItem['index']];
                    return '' + (value === -1 ? 0 : value);
                },
            },
        },
    };
    doughnutChartData: CourseStatisticsDataSet[] = [
        {
            data: [0, 0],
            backgroundColor: this.doughnutChartColors,
        },
    ];

    ngOnInit(): void {
        this.stats = [this.currentAbsolute, this.currentMax - this.currentAbsolute];
        this.doughnutChartData[0].data = this.stats;
        if (this.currentMax === 0) {
            // [0, 0] will lead to the chart not being displayed - is further handled in the option tooltips
            this.doughnutChartData[0].data = [-1, 0];
        }
    }
}
