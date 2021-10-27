import { NgModule } from '@angular/core';
import { ArtemisAssessmentDashboardModule } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard.module';
import { ArtemisExerciseAssessmentDashboardModule } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.module';

@NgModule({
    imports: [ArtemisAssessmentDashboardModule, ArtemisExerciseAssessmentDashboardModule],
})
export class ArtemisDashboardsModule {}
