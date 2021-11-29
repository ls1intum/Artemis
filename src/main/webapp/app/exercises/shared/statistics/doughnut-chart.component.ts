import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { CourseStatisticsDataSet } from 'app/overview/course-statistics/course-statistics.component';
import { ChartType } from 'chart.js';
import { roundScoreSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { ExerciseType } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-doughnut-chart',
    templateUrl: './doughnut-chart.component.html',
    styleUrls: ['./doughnut-chart.component.scss'],
})
export class DoughnutChartComponent implements OnChanges, OnInit {
    @Input() course: Course;
    @Input() contentType: DoughnutChartType;
    @Input() exerciseId: number;
    @Input() exerciseType: ExerciseType;
    @Input() currentPercentage: number | undefined;
    @Input() currentAbsolute: number | undefined;
    @Input() currentMax: number | undefined;

    receivedStats = false;
    doughnutChartTitle: string;
    stats: number[];
    titleLink: string[] | undefined;

    // Icons
    faSpinner = faSpinner;

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
                    if (data && data['datasets'] && data['datasets'][0] && data['datasets'][0]['data']) {
                        const value = data['datasets'][0]['data'][tooltipItem['index']];
                        return '' + (value === -1 ? 0 : value);
                    }
                    return '';
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
            const remaining = roundScoreSpecifiedByCourseSettings(this.currentMax! - this.currentAbsolute!, this.course);
            this.stats = [this.currentAbsolute!, remaining];
            this.doughnutChartData[0].data = this.currentMax === 0 ? [-1, 0] : this.stats;
        }
    }

    /**
     * Depending on the information we want to display in the doughnut chart, we need different titles and links
     */
    ngOnInit(): void {
        switch (this.contentType) {
            case DoughnutChartType.AVERAGE_EXERCISE_SCORE:
                this.doughnutChartTitle = 'averageScore';
                this.titleLink = [`/course-management/${this.course.id}/${this.exerciseType}-exercises/${this.exerciseId}/scores`];
                break;
            case DoughnutChartType.PARTICIPATIONS:
                this.doughnutChartTitle = 'participationRate';
                this.titleLink = [`/course-management/${this.course.id}/${this.exerciseType}-exercises/${this.exerciseId}/participations`];
                break;
            case DoughnutChartType.QUESTIONS:
                this.doughnutChartTitle = 'resolved_posts';
                this.titleLink = [`/courses/${this.course.id}/exercises/${this.exerciseId}`];
                break;
            default:
                this.doughnutChartTitle = '';
                this.titleLink = undefined;
        }
    }

    /**
     * handles clicks onto the graph, which then redirects the user to the corresponding page,
     * e.g. participations to the participations page
     */
    openCorrespondingPage() {
        if (this.course.id && this.exerciseId && this.titleLink) {
            this.router.navigate(this.titleLink);
        }
    }
}
