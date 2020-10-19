import { NgModule } from '@angular/core';
import { ArtemisAssessmentDashboardModule } from 'app/course/dashboards/assessment-dashboard/assessment-dashboard.module';
import { ArtemisTutorExerciseDashboardModule } from 'app/exercises/shared/dashboards/tutor/exercise-assessment-dashboard.module';
import { ArtemisInstructorCourseStatsDashboardModule } from 'app/course/dashboards/instructor-course-dashboard/instructor-course-dashboard.module';
import { ArtemisInstructorExerciseStatsDashboardModule } from 'app/exercises/shared/dashboards/instructor/instructor-exercise-dashboard.module';

@NgModule({
    imports: [ArtemisAssessmentDashboardModule, ArtemisTutorExerciseDashboardModule, ArtemisInstructorCourseStatsDashboardModule, ArtemisInstructorExerciseStatsDashboardModule],
})
export class ArtemisDashboardsModule {}
