import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Graphs } from 'app/entities/statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';

@Component({
    selector: 'jhi-course-management-overview-statistics',
    templateUrl: './course-management-overview-statistics.component.html',
})
export class CourseManagementOverviewStatisticsComponent implements OnInit, OnChanges {
    @Input()
    amountOfStudentsInCourse: number;

    @Input()
    initialStats: number[] | undefined;

    loading = true;
    graphType: Graphs = Graphs.ACTIVE_STUDENTS;

    // Histogram-related properties
    amountOfStudents: string;

    // Data
    lineChartLabels: string[] = [];

    // ngx-data
    ngxData: any[] = [];
    chartColor: Color = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: ['rgba(53,61,71,1)'], // color: black
    };

    constructor(private translateService: TranslateService) {}

    ngOnInit() {
        this.amountOfStudents = this.translateService.instant('artemisApp.courseStatistics.amountOfStudents');

        for (let i = 0; i < 4; i++) {
            this.lineChartLabels[i] = this.translateService.instant(`overview.${3 - i}_weeks_ago`);
        }

        this.createChartData();
    }

    ngOnChanges() {
        if (!!this.initialStats) {
            this.loading = false;
            this.createChartData();
        }
    }

    /**
     * Creates chart in order to visualize data provided by the inputs
     * @private
     */
    private createChartData(): void {
        const set: any[] = [];
        this.ngxData = [];
        if (this.amountOfStudentsInCourse > 0 && !!this.initialStats) {
            this.initialStats.forEach((value, index) => {
                set.push({ name: this.lineChartLabels[index], value: (value * 100) / this.amountOfStudentsInCourse, absoluteValue: value });
            });
        } else {
            this.lineChartLabels.forEach((label) => {
                set.push({ name: label, value: 0 });
            });
        }
        this.ngxData.push({ name: 'active students', series: set });
    }

    /**
     * Appends a percentage sign to every tick on the y axis
     * @param value the default tick
     */
    formatYAxis(value: any): string {
        return value.toLocaleString() + ' %';
    }
}
