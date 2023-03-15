import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { BarChartModule, LineChartModule, PieChartModule } from '@swimlane/ngx-charts';

import { DoughnutChartComponent } from 'app/exercises/shared/statistics/doughnut-chart.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { StatisticsScoreDistributionGraphComponent } from 'app/shared/statistics-graph/statistics-score-distribution-graph.component';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule, BarChartModule, LineChartModule, PieChartModule],
    declarations: [DoughnutChartComponent, StatisticsAverageScoreGraphComponent, StatisticsGraphComponent, StatisticsScoreDistributionGraphComponent],
    exports: [DoughnutChartComponent, StatisticsAverageScoreGraphComponent, StatisticsGraphComponent, StatisticsScoreDistributionGraphComponent],
})
export class ArtemisChartsModule {}
