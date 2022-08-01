import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { examLiveStatisticsState } from 'app/exam/statistics/exam-live-statistics.route';
import { ExamLiveStatisticsComponent } from 'app/exam/statistics/exam-live-statistics.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { LiveStatisticsOverviewComponent } from 'app/exam/statistics/subpages/overview/live-statistics-overview.component';
import { LiveStatisticsExercisesComponent } from 'app/exam/statistics/subpages/exercise/live-statistics-exercises.component';
import { LiveStatisticsCardComponent } from 'app/exam/statistics/subpages/live-statistics-card.component';
import { LiveStatisticsActivityLogComponent } from 'app/exam/statistics/subpages/activity-log/live-statistics-activity-log.component';
import { BarChartModule, LineChartModule, PieChartModule } from '@swimlane/ngx-charts';
import { ExerciseChartComponent } from 'app/exam/statistics/charts/exercises/exercise-chart.component';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { ExerciseGroupChartComponent } from 'app/exam/statistics/charts/exercises/exercise-group-chart.component';
import { TotalActionsChartComponent } from 'app/exam/statistics/charts/activity-log/total-actions-chart.component';
import { AverageActionsChartComponent } from 'app/exam/statistics/charts/activity-log/average-actions-chart.component';
import { CategoryActionsChartComponent } from 'app/exam/statistics/charts/activity-log/category-actions-chart.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ChartTitleComponent } from 'app/exam/statistics/charts/chart-title.component';
import { ExerciseSubmissionChartComponent } from 'app/exam/statistics/charts/exercises/exercise-submission-chart.component';
import { ExerciseNavigationChartComponent } from 'app/exam/statistics/charts/exercises/exercise-navigation-chart.component';
import { ActionsChartComponent } from 'app/exam/statistics/charts/activity-log/actions-chart.component';
import { ExerciseTemplateChartComponent } from 'app/exam/statistics/charts/exercises/exercise-template-chart.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { LiveStatisticsExerciseDetailComponent } from 'app/exam/statistics/subpages/exercise/live-statistics-exercise-detail.component';
import { ExerciseDetailTemplateChartComponent } from 'app/exam/statistics/charts/exercise-detail/exercise-detail-template-chart.component';
import { ExerciseDetailSubmissionChartComponent } from 'app/exam/statistics/charts/exercise-detail/exercise-detail-submission-chart.component';
import { ExerciseDetailNavigationChartComponent } from 'app/exam/statistics/charts/exercise-detail/exercise-detail-navigation-chart.component';
import { ExerciseDetailCurrentChartComponent } from 'app/exam/statistics/charts/exercise-detail/exercise-detail-current-chart.component';

const ENTITY_STATES = [...examLiveStatisticsState];

@NgModule({
    imports: [
        RouterModule.forChild(ENTITY_STATES),
        ArtemisSharedCommonModule,
        BarChartModule,
        LineChartModule,
        PieChartModule,
        ArtemisSidePanelModule,
        ArtemisSharedComponentModule,
        ArtemisDataTableModule,
        NgxDatatableModule,
    ],
    declarations: [
        ExamLiveStatisticsComponent,
        LiveStatisticsOverviewComponent,
        LiveStatisticsExercisesComponent,
        LiveStatisticsActivityLogComponent,
        LiveStatisticsCardComponent,
        ExerciseTemplateChartComponent,
        ExerciseChartComponent,
        ExerciseGroupChartComponent,
        ExerciseSubmissionChartComponent,
        ExerciseNavigationChartComponent,
        ActionsChartComponent,
        TotalActionsChartComponent,
        AverageActionsChartComponent,
        CategoryActionsChartComponent,
        ChartTitleComponent,
        LiveStatisticsExerciseDetailComponent,
        ExerciseDetailTemplateChartComponent,
        ExerciseDetailSubmissionChartComponent,
        ExerciseDetailNavigationChartComponent,
        ExerciseDetailCurrentChartComponent,
    ],
})
export class ArtemisExamLiveStatisticsModule {}
