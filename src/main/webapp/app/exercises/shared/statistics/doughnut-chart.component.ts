import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { ExerciseType } from 'app/entities/exercise.model';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import { ScaleType, Color } from '@swimlane/ngx-charts';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/entities/statistics.model';

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

    // ngx
    ngxDoughnutData: NgxChartsSingleSeriesDataEntry[] = [
        { name: 'Done', value: 0 },
        { name: 'Not done', value: 0 },
    ];
    ngxColor = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.GREEN, GraphColors.RED],
    } as Color;
    bindFormatting = this.valueFormatting.bind(this);

    ngOnChanges(): void {
        // [0, 0] will lead to the chart not being displayed,
        // assigning [-1, 0] works around this issue and displays 0 %, 0 / 0 with a green circle
        if (this.currentAbsolute == undefined && !this.receivedStats) {
            this.assignValuesToData([1, 0]);
        } else {
            this.receivedStats = true;
            const remaining = roundValueSpecifiedByCourseSettings(this.currentMax! - this.currentAbsolute!, this.course);
            this.stats = [this.currentAbsolute!, remaining];
            return this.currentMax === 0 ? this.assignValuesToData([1, 0]) : this.assignValuesToData(this.stats);
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

    /**
     * Assigns a given array of numbers to ngxData
     * @param values the values that should be displayed by the chart
     * @private
     */
    private assignValuesToData(values: number[]) {
        this.ngxDoughnutData[0].value = values[0];
        this.ngxDoughnutData[1].value = values[1];
        this.ngxDoughnutData.forEach((entry: NgxChartsSingleSeriesDataEntry, index: number) => (entry.value = values[index]));
        this.ngxDoughnutData = [...this.ngxDoughnutData];
    }

    /**
     * Modifies the tooltip content of the chart.
     * @param data a dedicated object passed by ngx-charts
     * @returns absolute value represented by doughnut piece or 0 if the currentMax is 0.
     * This is necessary in order to compensate the workaround for
     * displaying a chart even if no values are there to display (i.e. currentMax is 0)
     */
    valueFormatting(data: any): string {
        return this.currentMax === 0 ? '0' : data.value;
    }
}
