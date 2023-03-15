import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { BarChartModule, LineChartModule, PieChartModule } from '@swimlane/ngx-charts';

import { ActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/actions-chart.component';
import { AverageActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/average-actions-chart.component';
import { CategoryActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/category-actions-chart.component';
import { TotalActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/total-actions-chart.component';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import { ExerciseDetailCurrentChartComponent } from 'app/exam/monitoring/charts/exercise-detail/exercise-detail-current-chart.component';
import { ExerciseDetailNavigationChartComponent } from 'app/exam/monitoring/charts/exercise-detail/exercise-detail-navigation-chart.component';
import { ExerciseDetailSubmissionChartComponent } from 'app/exam/monitoring/charts/exercise-detail/exercise-detail-submission-chart.component';
import { ExerciseDetailTemplateChartComponent } from 'app/exam/monitoring/charts/exercise-detail/exercise-detail-template-chart.component';
import { ExerciseChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-chart.component';
import { ExerciseGroupChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-group-chart.component';
import { ExerciseNavigationChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-navigation-chart.component';
import { ExerciseSubmissionChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-submission-chart.component';
import { ExerciseTemplateChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-template-chart.component';
import { ExamMonitoringComponent } from 'app/exam/monitoring/exam-monitoring.component';
import { examMonitoringState } from 'app/exam/monitoring/exam-monitoring.route';
import { MonitoringActivityLogComponent } from 'app/exam/monitoring/subpages/activity-log/monitoring-activity-log.component';
import { MonitoringExerciseDetailComponent } from 'app/exam/monitoring/subpages/exercise/monitoring-exercise-detail.component';
import { MonitoringExercisesComponent } from 'app/exam/monitoring/subpages/exercise/monitoring-exercises.component';
import { MonitoringCardComponent } from 'app/exam/monitoring/subpages/monitoring-card.component';
import { MonitoringOverviewComponent } from 'app/exam/monitoring/subpages/overview/monitoring-overview.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';

const ENTITY_STATES = [...examMonitoringState];

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
        FeatureToggleModule,
    ],
    declarations: [
        ExamMonitoringComponent,
        MonitoringOverviewComponent,
        MonitoringExercisesComponent,
        MonitoringActivityLogComponent,
        MonitoringCardComponent,
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
        MonitoringExerciseDetailComponent,
        ExerciseDetailTemplateChartComponent,
        ExerciseDetailSubmissionChartComponent,
        ExerciseDetailNavigationChartComponent,
        ExerciseDetailCurrentChartComponent,
    ],
})
export class ArtemisExamMonitoringModule {}
