import { NgModule } from '@angular/core';
import { NgxChartsModule, PieChartModule } from '@swimlane/ngx-charts';

import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { CourseExamAttemptReviewDetailComponent } from 'app/overview/course-exams/course-exam-attempt-review-detail/course-exam-attempt-review-detail.component';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { CoursesComponent } from 'app/overview/courses.component';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { HeaderCourseComponent } from 'app/overview/header-course.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';

@NgModule({
    imports: [
        ArtemisExerciseButtonsModule,
        ArtemisCourseExerciseRowModule,
        ArtemisSharedModule,
        ArtemisSharedPipesModule,
        ArtemisSidePanelModule,
        ArtemisCoursesRoutingModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        OrionModule,
        ArtemisComplaintsModule,
        FeatureToggleModule,
        RatingModule,
        NgxChartsModule,
        ArtemisExamSharedModule,
        PieChartModule,
    ],
    declarations: [
        CoursesComponent,
        CourseOverviewComponent,
        HeaderCourseComponent,
        CourseCardComponent,
        CourseExercisesComponent,
        CourseLecturesComponent,
        CourseLectureRowComponent,
        CourseExamsComponent,
        CourseExamDetailComponent,
        CourseExamAttemptReviewDetailComponent,
    ],
    exports: [HeaderCourseComponent],
})
export class ArtemisCoursesModule {}
