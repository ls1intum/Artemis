import { NgModule } from '@angular/core';
import { ArtemisTutorCourseDashboardModule } from 'app/course/dashboards/tutor-course-dashboard/tutor-course-dashboard.module';
import { ArtemisTutorExerciseDashboardModule } from 'app/exercises/shared/dashboards/tutor/tutor-exercise-dashboard.module';
import { ArtemisInstructorCourseStatsDashboardModule } from 'app/course/dashboards/instructor-course-dashboard/instructor-course-dashboard.module';
import { ArtemisInstructorExerciseStatsDashboardModule } from 'app/exercises/shared/dashboards/instructor/instructor-exercise-dashboard.module';

@NgModule({
    imports: [ArtemisTutorCourseDashboardModule, ArtemisTutorExerciseDashboardModule, ArtemisInstructorCourseStatsDashboardModule, ArtemisInstructorExerciseStatsDashboardModule],
})
export class ArtemisDashboardsModule {}
