import { NgModule } from '@angular/core';
import { DoughnutChartComponent } from 'app/exercises/shared/statistics/doughnut-chart.component';
import { ChartComponent } from 'app/shared/chart/chart.component';
import { StatisticsAverageScoreGraphComponent } from 'app/shared/statistics-graph/statistics-average-score-graph.component';
import { StatisticsGraphComponent } from 'app/shared/statistics-graph/statistics-graph.component';
import { StatisticsScoreDistributionGraphComponent } from 'app/shared/statistics-graph/statistics-score-distribution-graph.component';
import { ChartsModule } from 'ng2-charts';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { RouterModule } from '@angular/router';
import { NgxChartsModule } from '@swimlane/ngx-charts';

@NgModule({
    imports: [ArtemisSharedModule, ChartsModule, RouterModule, NgxChartsModule],
    declarations: [DoughnutChartComponent, ChartComponent, StatisticsAverageScoreGraphComponent, StatisticsGraphComponent, StatisticsScoreDistributionGraphComponent],
    exports: [DoughnutChartComponent, ChartComponent, StatisticsAverageScoreGraphComponent, StatisticsGraphComponent, StatisticsScoreDistributionGraphComponent],
})
export class Ng2ChartsModule {}
