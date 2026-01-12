import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { DoughnutChartType } from './course-detail.component';
import { Router, RouterLink } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Color, PieChartModule, ScaleType } from '@swimlane/ngx-charts';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

const PIE_CHART_NA_FALLBACK_VALUE = [0, 0, 1];

@Component({
    selector: 'jhi-course-detail-doughnut-chart',
    templateUrl: './course-detail-doughnut-chart.component.html',
    styleUrls: ['./course-detail-doughnut-chart.component.scss'],
    imports: [RouterLink, NgClass, FaIconComponent, PieChartModule, ArtemisTranslatePipe],
})
export class CourseDetailDoughnutChartComponent {
    private router = inject(Router);

    readonly contentType = input.required<DoughnutChartType>();
    readonly currentPercentage = input<number>();
    readonly currentAbsolute = input<number>();
    readonly currentMax = input<number>();
    readonly course = input.required<Course>();
    readonly showText = input<string>();

    readonly receivedStats = signal(false);
    readonly stats = signal<number[]>([]);
    readonly ngxData = signal<NgxChartsSingleSeriesDataEntry[]>([
        { name: 'Done', value: 0 },
        { name: 'Not done', value: 0 },
        { name: 'N/A', value: 0 }, // fallback to display grey circle if there is no maxValue
    ]);

    // Icons
    faSpinner = faSpinner;

    // ngx-charts
    ngxColor = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.GREEN, GraphColors.RED, GraphColors.LIGHT_GREY],
    } as Color;
    bindFormatting = this.valueFormatting.bind(this);

    // Computed values for title and link based on contentType
    readonly doughnutChartTitle = computed(() => {
        switch (this.contentType()) {
            case DoughnutChartType.ASSESSMENT:
                return 'assessments';
            case DoughnutChartType.COMPLAINTS:
                return 'complaints';
            case DoughnutChartType.FEEDBACK:
                return 'moreFeedback';
            case DoughnutChartType.AVERAGE_COURSE_SCORE:
                return 'averageStudentScore';
            case DoughnutChartType.CURRENT_LLM_COST:
                return 'currentTotalLLMCost';
            default:
                return '';
        }
    });

    readonly titleLink = computed(() => {
        switch (this.contentType()) {
            case DoughnutChartType.ASSESSMENT:
                return 'assessment-dashboard';
            case DoughnutChartType.COMPLAINTS:
                return 'complaints';
            case DoughnutChartType.FEEDBACK:
                return 'more-feedback-requests';
            case DoughnutChartType.AVERAGE_COURSE_SCORE:
                if (this.course().isAtLeastInstructor) {
                    return 'scores';
                }
                return undefined;
            case DoughnutChartType.CURRENT_LLM_COST:
                return undefined;
            default:
                return undefined;
        }
    });

    constructor() {
        // Effect to handle ngOnChanges logic for input changes
        effect(() => {
            const currentAbsolute = this.currentAbsolute();
            const currentMax = this.currentMax();
            const showText = this.showText();
            const course = this.course();

            untracked(() => {
                // [0, 0, 0] will lead to the chart not being displayed,
                // assigning [0, 0, 1] (PIE_CHART_NA_FALLBACK_VALUE) works around this issue and displays 0 %, 0 / 0 with a grey circle
                if (currentAbsolute == undefined && !this.receivedStats() && !showText) {
                    this.updatePieChartData(PIE_CHART_NA_FALLBACK_VALUE);
                } else {
                    this.receivedStats.set(true);
                    const remaining = roundValueSpecifiedByCourseSettings(currentMax! - currentAbsolute!, course);
                    this.stats.set([currentAbsolute!, remaining, 0]); // done, not done, na
                    if (currentMax === 0) {
                        this.updatePieChartData(PIE_CHART_NA_FALLBACK_VALUE);
                    } else {
                        this.updatePieChartData(this.stats());
                    }
                }
            });
        });

        // Effect to handle contentType initialization (for AVERAGE_COURSE_SCORE name change)
        effect(() => {
            const contentType = this.contentType();
            untracked(() => {
                if (contentType === DoughnutChartType.AVERAGE_COURSE_SCORE) {
                    const currentData = this.ngxData();
                    currentData[0].name = 'Average score';
                    this.ngxData.set([...currentData]);
                }
            });
        });
    }

    /**
     * handles clicks onto the graph, which then redirects the user to the corresponding page, e.g. complaints page for the complaints chart
     */
    openCorrespondingPage() {
        const course = this.course();
        const titleLink = this.titleLink();
        if (course.id && titleLink) {
            this.router.navigate(['/course-management', course.id, titleLink]);
        }
    }

    /**
     * Assigns a given array of numbers to ngxData
     * @param values the values that should be displayed by the chart
     */
    private updatePieChartData(values: number[]): void {
        const currentData = this.ngxData();
        currentData.forEach((entry: NgxChartsSingleSeriesDataEntry, index: number) => (entry.value = values[index]));
        this.ngxData.set([...currentData]);
    }

    /**
     * Modifies the tooltip content of the chart.
     * Returns absolute value represented by doughnut piece or 0 if the currentMax is 0.
     * This is necessary in order to compensate the workaround for
     * displaying a chart even if no values are there to display (i.e. currentMax is 0)
     * @param data the default tooltip content that has to be replaced
     * returns string representing custom tooltip content
     */
    valueFormatting(data: any): string {
        return this.currentMax() === 0 ? '0' : data.value;
    }
}
