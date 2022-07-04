import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { examMonitoringState } from 'app/exam/monitoring/exam-monitoring.route';
import { ExamMonitoringComponent } from 'app/exam/monitoring/exam-monitoring.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { MonitoringOverviewComponent } from 'app/exam/monitoring/subpages/overview/monitoring-overview.component';
import { MonitoringExercisesComponent } from 'app/exam/monitoring/subpages/exercise/monitoring-exercises.component';
import { MonitoringCardComponent } from 'app/exam/monitoring/subpages/monitoring-card.component';
import { MonitoringActivityLogComponent } from 'app/exam/monitoring/subpages/activity-log/monitoring-activity-log.component';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { ExerciseChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-chart.component';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { ExerciseGroupChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-group-chart.component';
import { TotalActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/total-actions-chart.component';
import { AverageActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/average-actions-chart.component';
import { CategoryActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/category-actions-chart.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ChartTitleComponent } from 'app/exam/monitoring/charts/chart-title.component';
import { ExerciseSubmissionChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-submission-chart.component';
import { ExerciseNavigationChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-navigation-chart.component';
import { ActionsChartComponent } from 'app/exam/monitoring/charts/activity-log/actions-chart.component';
import { ExerciseTemplateChartComponent } from 'app/exam/monitoring/charts/exercises/exercise-template-chart.component';

const ENTITY_STATES = [...examMonitoringState];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedCommonModule, NgxChartsModule, ArtemisSidePanelModule, ArtemisSharedComponentModule],
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
    ],
})
export class ArtemisExamMonitoringModule {}
