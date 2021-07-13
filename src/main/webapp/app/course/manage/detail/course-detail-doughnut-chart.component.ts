import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { CourseStatisticsDataSet } from 'app/overview/course-statistics/course-statistics.component';
import { ChartType } from 'chart.js';
import { round } from 'app/shared/util/utils';
import { DoughnutChartType } from './course-detail.component';
import { Router } from '@angular/router';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-course-detail-doughnut-chart',
    templateUrl: './course-detail-doughnut-chart.component.html',
    styleUrls: ['./course-detail-doughnut-chart.component.scss'],
})
export class CourseDetailDoughnutChartComponent implements OnChanges, OnInit {
    @Input() contentType: DoughnutChartType;
    @Input() currentPercentage: number | undefined;
    @Input() currentAbsolute: number | undefined;
    @Input() currentMax: number | undefined;
    @Input() course: Course;

    receivedStats = false;
    doughnutChartTitle: string;
    stats: number[];
    titleLink: string | undefined;

    constructor(private router: Router) {}

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

    ngOnChanges(): void {
        // [0, 0] will lead to the chart not being displayed,
        // assigning [-1, 0] works around this issue and displays 0 %, 0 / 0 with a green circle
        if (this.currentAbsolute == undefined && !this.receivedStats) {
            this.doughnutChartData[0].data = [-1, 0];
        } else {
            this.receivedStats = true;
            const remaining = round(this.currentMax! - this.currentAbsolute!, 1);
            this.stats = [this.currentAbsolute!, remaining];
            this.doughnutChartData[0].data = this.currentMax === 0 ? [-1, 0] : this.stats;
        }
    }

    /**
     * Depending on the information we want to display in the doughnut chart, we need different titles and links
     */
    ngOnInit(): void {
        switch (this.contentType) {
            case DoughnutChartType.ASSESSMENT:
                this.doughnutChartTitle = 'assessments';
                this.titleLink = 'assessment-dashboard';
                break;
            case DoughnutChartType.COMPLAINTS:
                this.doughnutChartTitle = 'complaints';
                this.titleLink = 'complaints';
                break;
            case DoughnutChartType.FEEDBACK:
                this.doughnutChartTitle = 'moreFeedback';
                this.titleLink = undefined;
                break;
            case DoughnutChartType.AVERAGE_COURSE_SCORE:
                this.doughnutChartTitle = 'averageStudentScore';
                this.titleLink = undefined;
                if (this.course.isAtLeastInstructor) {
                    this.titleLink = 'scores';
                }
                break;
            default:
                this.doughnutChartTitle = '';
                this.titleLink = undefined;
        }
    }

    /**
     * handles clicks onto the graph, which then redirects the user to the corresponding page, e.g. complaints page for the complaints chart
     */
    openCorrespondingPage() {
        if (this.course.id && this.titleLink) {
            this.router.navigate(['/course-management', this.course.id, this.titleLink]);
        }
    }
}
