import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { round } from 'app/shared/util/utils';
import { getGraphColorForTheme, GraphColors } from 'app/entities/statistics.model';
import { axisTickFormattingWithPercentageSign } from 'app/shared/statistics-graph/statistics-graph.utils';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Subscription } from 'rxjs';
import { ThemeService } from 'app/core/theme/theme.service';

@Component({
    selector: 'jhi-statistics-score-distribution-graph',
    templateUrl: './statistics-score-distribution-graph.component.html',
    styleUrls: ['../chart/vertical-bar-chart.scss'],
})
export class StatisticsScoreDistributionGraphComponent implements OnInit, OnDestroy {
    @Input()
    averageScoreOfExercise: number | undefined;
    @Input()
    scoreDistribution: number[] | undefined;
    @Input()
    numberOfExerciseScores: number | undefined;

    // Histogram related properties
    exerciseAverageLegend: string;
    distributionLegend: string;
    chartLegend = true;

    // ngx
    ngxData: any[] = [];
    ngxColor: Color = {
        name: 'Statistics',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    };
    yAxisTickFormatting = axisTickFormattingWithPercentageSign;

    themeSubscription: Subscription;

    // Data
    barChartLabels: string[] = [];
    relativeChartData: number[] = [];

    constructor(private themeService: ThemeService) {
        this.themeSubscription = this.themeService
            .getCurrentThemeObservable()
            .subscribe((theme) => (this.ngxColor = { ...this.ngxColor, domain: [getGraphColorForTheme(theme, GraphColors.DARK_BLUE)] }));
    }

    ngOnInit(): void {
        this.initializeChart();
    }

    ngOnDestroy() {
        this.themeSubscription?.unsubscribe();
    }

    private initializeChart(): void {
        this.barChartLabels = ['[0, 10)', '[10, 20)', '[20, 30)', '[30, 40)', '[40, 50)', '[50, 60)', '[60, 70)', '[70, 80)', '[80, 90)', '[90, 100]'];
        if (this.numberOfExerciseScores && this.numberOfExerciseScores > 0) {
            this.relativeChartData = [];
            for (const value of this.scoreDistribution!) {
                this.relativeChartData.push(round((value * 100) / this.numberOfExerciseScores));
            }
        } else {
            this.relativeChartData = new Array(10).fill(0);
        }
        this.ngxData = this.relativeChartData.map((data, index) => ({ name: this.barChartLabels[index], value: data }));
    }

    /**
     * Finds given the distribution bucket the corresponding absolute value
     * @param bucket the distribution bucket to determine the absolute Value for
     * @returns amount of submissions that achieved a score in the buckets range
     */
    lookUpAbsoluteValue(bucket: string) {
        const index = this.barChartLabels.indexOf(bucket);
        return this.scoreDistribution![index];
    }
}
