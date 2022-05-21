import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { examMonitoringState } from 'app/exam/monitoring/exam-monitoring.route';
import { ExamMonitoringComponent } from 'app/exam/monitoring/exam-monitoring.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { MonitoringOverviewComponent } from 'app/exam/monitoring/subpages/overview/monitoring-overview.component';
import { MonitoringExercisesComponent } from 'app/exam/monitoring/subpages/exercise/monitoring-exercises.component';

const ENTITY_STATES = [...examMonitoringState];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedCommonModule],
    declarations: [ExamMonitoringComponent, MonitoringOverviewComponent, MonitoringExercisesComponent],
})
export class ArtemisExamMonitoringModule {}
