import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ChartsModule } from 'ng2-charts';
import { ClipboardModule } from 'ngx-clipboard';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisProgrammingExerciseModule } from 'app/entities/programming-exercise/programming-exercise.module';
import { ArtemisSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';
import { ArtemisCourseRegistrationSelector } from 'app/components/course-registration-selector/course-registration-selector.module';
import { OrionModule } from 'app/orion/orion.module';
import { FeatureToggleModule } from 'app/feature-toggle/feature-toggle.module';
import { ProgrammingExerciseUtilsModule } from 'app/entities/programming-exercise/utils/programming-exercise-utils.module';
import { ProgrammingExerciseStudentIdeActionsComponent } from 'app/overview/exercise-details/programming-exercise-student-ide-actions.component';
import { OverviewCourseCardComponent } from 'app/overview/overview-course-card.component';
import { ArtemisStudentQuestionsModule } from 'app/student-questions/student-questions.module';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { ExerciseActionButtonComponent } from 'app/overview/exercise-details/exercise-action-button.component';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers/exercise-headers.module';
import { ArtemisResultModule } from 'app/entities/result/result.module';
import { OverviewComponent } from 'app/overview/overview.component';
import { OVERVIEW_ROUTES } from 'app/overview/overview.route';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';

const ENTITY_STATES = [...OVERVIEW_ROUTES];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ChartsModule,
        ClipboardModule,
        MomentModule,
        ArtemisResultModule,
        ArtemisProgrammingExerciseModule,
        ArtemisStudentQuestionsModule,
        ArtemisSidePanelModule,
        RouterModule.forChild(ENTITY_STATES),
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisCourseRegistrationSelector,
        OrionModule,
        ArtemisComplaintsModule,
        FeatureToggleModule,
        ProgrammingExerciseUtilsModule,
    ],
    declarations: [
        OverviewComponent,
        CourseOverviewComponent,
        OverviewCourseCardComponent,
        CourseStatisticsComponent,
        CourseExerciseRowComponent,
        CourseExercisesComponent,
        CourseExerciseDetailsComponent,
        CourseLecturesComponent,
        CourseLectureRowComponent,
        CourseLectureDetailsComponent,
        ExerciseActionButtonComponent,
        ExerciseDetailsStudentActionsComponent,
        ProgrammingExerciseStudentIdeActionsComponent,
    ],
    entryComponents: [],
    exports: [ExerciseActionButtonComponent],
})
export class ArtemisOverviewModule {}
