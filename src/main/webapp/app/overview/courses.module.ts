import { NgModule } from '@angular/core';
import { ChartsModule } from 'ng2-charts';
import { ClipboardModule } from 'ngx-clipboard';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';
import { OrionModule } from 'app/shared/orion/orion.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { CoursesComponent } from 'app/overview/courses.component';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { CourseRegistrationSelectorComponent } from 'app/overview/course-registration-selector/course-registration-selector.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { CourseLearningGoalsComponent } from './course-learning-goals/course-learning-goals.component';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { CourseExerciseDetailsModule } from 'app/overview/exercise-details/course-exercise-details.module';
import { ArtemisExerciseScoresChartModule } from 'app/overview/visualizations/exercise-scores-chart.module';

@NgModule({
    imports: [
        ArtemisExerciseScoresChartModule,
        ArtemisExerciseButtonsModule,
        ArtemisCourseExerciseRowModule,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ChartsModule,
        ClipboardModule,
        MomentModule,
        ArtemisSharedPipesModule,
        ArtemisResultModule,
        ArtemisSidePanelModule,
        ArtemisCoursesRoutingModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        OrionModule,
        ArtemisComplaintsModule,
        FeatureToggleModule,
        ArtemisTeamModule,
        RatingModule,
        ArtemisLearningGoalsModule,
        CourseExerciseDetailsModule, // Important: at the moment, we cannot lazy load this module, because otherwise the LTI integration won't work any more
    ],
    declarations: [
        CoursesComponent,
        CourseOverviewComponent,
        CourseRegistrationSelectorComponent,
        CourseCardComponent,
        CourseStatisticsComponent,
        CourseExercisesComponent,
        CourseLecturesComponent,
        CourseLectureRowComponent,
        CourseExamsComponent,
        CourseExamDetailComponent,
        CourseLearningGoalsComponent,
    ],
})
export class ArtemisCoursesModule {}
