import { Component, OnInit, inject, input } from '@angular/core';
import { round } from 'app/foundation/util/utils';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { axisTickFormattingWithPercentageSign } from 'app/exercise/statistics-graph/util/statistics-graph.utils';
import { BarChartModule, Color, ScaleType } from '@swimlane/ngx-charts';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-statistics-score-distribution-graph',
    templateUrl: './statistics-score-distribution-graph.component.html',
    styleUrls: ['../../chart/vertical-bar-chart.scss'],
    imports: [TranslateDirective, BarChartModule],
})
export class StatisticsScoreDistributionGraphComponent implements OnInit {
    private navigationService = inject(ArtemisNavigationUtilService);

    readonly averageScoreOfExercise = input<number>();
    readonly scoreDistribution = input<number[]>();
    readonly numberOfExerciseScores = input<number>();
    readonly exerciseType = input.required<ExerciseType>();
    readonly courseId = input.required<number>();
    readonly exerciseId = input.required<number>();

    // ngx
    ngxData: any[] = [];
    ngxColor: Color = {
        name: 'Statistics',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.DARK_BLUE],
    };
    yAxisTickFormatting = axisTickFormattingWithPercentageSign;

    // Data
    barChartLabels: string[] = [];
    relativeChartData: number[] = [];

    ngOnInit(): void {
        this.initializeChart();
    }

    private initializeChart(): void {
        this.barChartLabels = ['[0, 10)', '[10, 20)', '[20, 30)', '[30, 40)', '[40, 50)', '[50, 60)', '[60, 70)', '[70, 80)', '[80, 90)', '[90, 100]'];
        const numberOfExerciseScores = this.numberOfExerciseScores();
        if (numberOfExerciseScores && numberOfExerciseScores > 0) {
            this.relativeChartData = [];
            for (const value of this.scoreDistribution() ?? []) {
                this.relativeChartData.push(round((value * 100) / numberOfExerciseScores));
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
        return this.scoreDistribution()?.[index];
    }

    /**
     * Handles the event if a user clicks on a certain chart bar
     * @param event the event passed by ngx-charts
     */
    selectChartBar(event: any): void {
        const index = this.barChartLabels.indexOf(event.name);
        const route = [`/course-management/${this.courseId()}/${this.exerciseType()}-exercises/${this.exerciseId()}/scores`];
        this.navigationService.routeInNewTab(route, { queryParams: { scoreRangeFilter: index } });
    }
}
