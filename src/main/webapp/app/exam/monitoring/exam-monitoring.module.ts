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
import { ExerciseChartComponent } from 'app/exam/monitoring/charts/exercise-chart/exercise-chart.component';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { ExerciseGroupChartComponent } from 'app/exam/monitoring/charts/exercise-group-chart/exercise-group-chart.component';

const ENTITY_STATES = [...examMonitoringState];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedCommonModule, NgxChartsModule, ArtemisSidePanelModule],
    declarations: [
        ExamMonitoringComponent,
        MonitoringOverviewComponent,
        MonitoringExercisesComponent,
        MonitoringActivityLogComponent,
        MonitoringCardComponent,
        ExerciseChartComponent,
        ExerciseGroupChartComponent,
    ],
})
export class ArtemisExamMonitoringModule {}
