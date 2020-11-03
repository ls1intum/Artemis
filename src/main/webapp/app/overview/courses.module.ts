import { NgModule } from '@angular/core';
import { ChartsModule } from 'ng2-charts';
import { ClipboardModule } from 'ngx-clipboard';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { CourseLectureRowComponent } from 'app/overview/course-lectures/course-lecture-row.component';
import { OrionModule } from 'app/shared/orion/orion.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ProgrammingExerciseUtilsModule } from 'app/exercises/programming/shared/utils/programming-exercise-utils.module';
import { ProgrammingExerciseStudentIdeActionsComponent } from 'app/overview/exercise-details/programming-exercise-student-ide-actions.component';
import { CourseCardComponent } from 'app/overview/course-card.component';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { ExerciseActionButtonComponent } from 'app/overview/exercise-details/exercise-action-button.component';
import { CourseOverviewComponent } from 'app/overview/course-overview.component';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { CourseLectureDetailsComponent } from 'app/overview/course-lectures/course-lecture-details.component';
import { CourseExercisesComponent } from 'app/overview/course-exercises/course-exercises.component';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { CoursesComponent } from 'app/overview/courses.component';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { CourseLecturesComponent } from 'app/overview/course-lectures/course-lectures.component';
import { ExerciseDetailsStudentActionsComponent } from 'app/overview/exercise-details/exercise-details-student-actions.component';
import { CourseExerciseRowComponent } from 'app/overview/course-exercises/course-exercise-row.component';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { CourseRegistrationSelectorComponent } from 'app/overview/course-registration-selector/course-registration-selector.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { CourseExamsComponent } from 'app/overview/course-exams/course-exams.component';
import { CourseExamDetailComponent } from 'app/overview/course-exams/course-exam-detail/course-exam-detail.component';
import { ExerciseUnitComponent } from './course-lectures/exercise-unit/exercise-unit.component';
import { AttachmentUnitComponent } from './course-lectures/attachment-unit/attachment-unit.component';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ChartsModule,
        ClipboardModule,
        MomentModule,
        ArtemisResultModule,
        ArtemisSidePanelModule,
        ArtemisCoursesRoutingModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        OrionModule,
        ArtemisComplaintsModule,
        FeatureToggleModule,
        ProgrammingExerciseUtilsModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        ArtemisTeamModule,
    ],
    declarations: [
        CoursesComponent,
        CourseOverviewComponent,
        CourseRegistrationSelectorComponent,
        CourseCardComponent,
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
        CourseExamsComponent,
        CourseExamDetailComponent,
        ExerciseUnitComponent,
        AttachmentUnitComponent,
    ],
    exports: [ExerciseActionButtonComponent, ExerciseDetailsStudentActionsComponent, ExerciseUnitComponent, AttachmentUnitComponent],
})
export class ArtemisCoursesModule {}
