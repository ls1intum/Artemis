import { NgModule } from '@angular/core';

import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';

import { CourseCardComponent } from 'app/overview/course-card.component';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CoursesComponent } from 'app/overview/courses.component';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { NgxChartsModule, PieChartModule } from '@swimlane/ngx-charts';
import { CourseUnenrollmentModalComponent } from 'app/overview/course-unenrollment-modal.component';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';

@NgModule({
    imports: [
        ArtemisExerciseButtonsModule,
        ArtemisCourseExerciseRowModule,

        ArtemisCoursesRoutingModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisComplaintsModule,
        NgxChartsModule,
        PieChartModule,
        CourseCardComponent,
        SearchFilterComponent,
        CoursesComponent,
        CourseOverviewComponent,
        CourseExercisesComponent,
        CourseLecturesComponent,
        CourseLectureRowComponent,
        CourseUnenrollmentModalComponent,
    ],
})
export class ArtemisCoursesModule {}
