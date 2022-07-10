import { NgModule } from '@angular/core';
import { DoughnutChartComponent } from 'app/exercises/shared/statistics/doughnut-chart.component';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { StatisticsScoreDistributionGraphComponent } from 'app/shared/statistics-graph/statistics-score-distribution-graph.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { BarChartModule, LineChartModule, PieChartModule } from '@swimlane/ngx-charts';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule, BarChartModule, LineChartModule, PieChartModule],
    declarations: [DoughnutChartComponent, StatisticsAverageScoreGraphComponent, StatisticsGraphComponent, StatisticsScoreDistributionGraphComponent],
    exports: [DoughnutChartComponent, StatisticsAverageScoreGraphComponent, StatisticsGraphComponent, StatisticsScoreDistributionGraphComponent],
})
export class ArtemisChartsModule {}
