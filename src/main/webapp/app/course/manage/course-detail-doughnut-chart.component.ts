import { Component, Input, OnInit } from '@angular/core';
import { CourseStatisticsDataSet } from 'app/overview/course-statistics/course-statistics.component';
import { ChartType } from 'chart.js';

@Component({
    selector: 'jhi-course-detail-doughnut-chart',
    templateUrl: './course-detail-doughnut-chart.component.html',
    styleUrls: ['./course-detail-doughnut-chart.component.scss'],
})
export class CourseDetailDoughnutChartComponent implements OnInit {
    @Input() doughnutChartTitle: string;

    @Input() currentPercentage: number;
    @Input() currentAbsolute: number;
    @Input() currentMax: number;

    stats: number[];

    // Chart.js data
    doughnutChartType: ChartType = 'doughnut';
    doughnutChartColors: any[] = ['limegreen', 'red'];
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
