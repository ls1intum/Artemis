import { NgModule } from '@angular/core';
import { ClipboardModule } from 'ngx-clipboard';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';
import { OrionModule } from 'app/shared/orion/orion.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { CoursesComponent } from 'app/overview/courses.component';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { NgxChartsModule } from '@swimlane/ngx-charts';

@NgModule({
    imports: [
        ArtemisExerciseButtonsModule,
        ArtemisCourseExerciseRowModule,
        ArtemisSharedModule,
        ClipboardModule,
        ArtemisSharedPipesModule,
        ArtemisSidePanelModule,
        ArtemisCoursesRoutingModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        OrionModule,
        ArtemisComplaintsModule,
        FeatureToggleModule,
        RatingModule,
        NgxChartsModule,
    ],
    declarations: [
        CoursesComponent,
        CourseOverviewComponent,
        CourseCardComponent,
        CourseExercisesComponent,
        CourseLecturesComponent,
        CourseLectureRowComponent,
        CourseExamsComponent,
        CourseExamDetailComponent,
    ],
})
export class ArtemisCoursesModule {}
