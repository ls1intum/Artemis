import { Component, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DoughnutChartType } from 'app/course/manage/detail/course-detail.component';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/course/shared/entities/course.model';
import { ChartModule } from 'primeng/chart';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { singleSeriesChartData } from 'app/shared-ui/chart/chart-adapters';
import { doughnutChartOptions } from 'app/shared-ui/chart/chart-options';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

const PIE_CHART_NA_FALLBACK_VALUE = [0, 0, 1];

@Component({
    selector: 'jhi-doughnut-chart',
    templateUrl: './doughnut-chart.component.html',
    styleUrls: ['./doughnut-chart.component.scss'],
    imports: [RouterLink, NgClass, FaIconComponent, ChartModule, ArtemisTranslatePipe],
})
export class DoughnutChartComponent implements OnInit {
    protected readonly faSpinner = faSpinner;

    private readonly router = inject(Router);

    readonly course = input<Course>(undefined!);
    readonly contentType = input<DoughnutChartType>(undefined!);
    readonly exerciseId = input<number>(undefined!);
    readonly exerciseType = input<ExerciseType>(undefined!);
    readonly currentPercentage = input<number>();
    readonly currentAbsolute = input<number>();
    readonly currentMax = input<number>();

    readonly receivedStats = signal(false);
    readonly doughnutChartTitle = signal<string>(undefined!);
    stats: number[];
    readonly titleLink = signal<string[] | undefined>(undefined);

    readonly chartEntries = signal<ChartSeriesEntry[]>([
        { name: 'Done', value: 0 },
        { name: 'Not done', value: 0 },
        { name: 'N/A', value: 0 }, // fallback to display grey circle if there is no maxValue
    ]);

    private readonly chartColors = inject(ChartColorService).resolvedColors(() => [GraphColors.GREEN, GraphColors.RED, GraphColors.LIGHT_GREY]);

    readonly chartData = computed(() => singleSeriesChartData(this.chartEntries(), this.chartColors()));
    readonly chartOptions = computed(() =>
        doughnutChartOptions({
            legend: false,
            tooltip: { label: (item) => `${item.label}: ${this.valueFormatting({ value: item.parsed })}` },
        }),
    );

    constructor() {
        // Recompute the doughnut data whenever the inputs change (replaces ngOnChanges).
        // Track the inputs, but run the chartEntries read+write inside untracked() to avoid a self-triggering loop.
        effect(() => {
            const currentAbsolute = this.currentAbsolute();
            const currentMax = this.currentMax();
            const course = this.course();
            untracked(() => {
                // [0, 0, 0] would hide the chart; PIE_CHART_NA_FALLBACK_VALUE displays 0 %, 0 / 0 with a grey circle.
                if (currentAbsolute == undefined && !this.receivedStats()) {
                    this.updatePieChartData(PIE_CHART_NA_FALLBACK_VALUE);
                } else {
                    this.receivedStats.set(true);
                    const remaining = roundValueSpecifiedByCourseSettings(currentMax! - currentAbsolute!, course);
                    this.stats = [currentAbsolute!, remaining, 0]; // done, not done, na
                    this.updatePieChartData(currentMax === 0 ? PIE_CHART_NA_FALLBACK_VALUE : this.stats);
                }
            });
        });
    }

    /**
     * Depending on the information we want to display in the doughnut chart, we need different titles and links
     */
    ngOnInit(): void {
        switch (this.contentType()) {
            case DoughnutChartType.AVERAGE_EXERCISE_SCORE:
                this.doughnutChartTitle.set('averageScore');
                this.titleLink.set([`/course-management/${this.course().id}/${this.exerciseType()}-exercises/${this.exerciseId()}/scores`]);
                break;
            case DoughnutChartType.PARTICIPATIONS:
                this.doughnutChartTitle.set('participationRate');
                this.titleLink.set([`/course-management/${this.course().id}/${this.exerciseType()}-exercises/${this.exerciseId()}/participations`]);
                break;
            case DoughnutChartType.QUESTIONS:
                this.doughnutChartTitle.set('resolved_posts');
                this.titleLink.set([`/courses/${this.course().id}/exercises/${this.exerciseId()}`]);
                break;
            default:
                this.doughnutChartTitle.set('');
                this.titleLink.set(undefined);
        }
    }

    /**
     * handles clicks onto the graph, which then redirects the user to the corresponding page,
     * e.g. participations to the participations page
     */
    openCorrespondingPage() {
        const titleLink = this.titleLink();
        if (this.course().id && this.exerciseId() && titleLink) {
            this.router.navigate(titleLink);
        }
    }

    /**
     * Assigns a given array of numbers to the chart entries
     * @param values the values that should be displayed by the chart
     */
    private updatePieChartData(values: number[]) {
        const currentData = this.chartEntries();
        currentData.forEach((entry: ChartSeriesEntry, index: number) => (entry.value = values[index]));
        this.chartEntries.set([...currentData]);
    }

    /**
     * Modifies the tooltip content of the chart.
     * @param data the data point hovered by the user
     * @returns absolute value represented by doughnut piece or 0 if the currentMax is 0.
     * This is necessary in order to compensate the workaround for
     * displaying a chart even if no values are there to display (i.e. currentMax is 0)
     */
    valueFormatting(data: { value: number }): string {
        return this.currentMax() === 0 ? '0' : String(data.value);
    }
}
