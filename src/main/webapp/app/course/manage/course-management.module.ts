import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisFileUploadExerciseManagementModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.module';
import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { ImageCropperModule } from 'ngx-image-cropper';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseManagementExercisesComponent } from 'app/course/manage/course-management-exercises.component';
import { CourseDetailComponent } from 'app/course/manage/course-detail.component';
import { CourseUpdateComponent } from 'app/course/manage/course-update.component';
import { courseManagementState } from 'app/course/manage/course-management.route';
import { CourseManagementComponent } from 'app/course/manage/course-management.component';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import { ArtemisQuizManagementModule } from 'app/exercises/quiz/manage/quiz-management.module';
import { ArtemisExerciseModule } from 'app/exercises/shared/exercise/exercise.module';
import { ArtemisLectureModule } from 'app/lecture/lecture.module';
import { ArtemisTextExerciseManagementModule } from 'app/exercises/text/manage/text-exercise-management.module';
import { ArtemisDashboardsModule } from 'app/shared/dashboards/dashboards.module';
import { ArtemisParticipationModule } from 'app/exercises/shared/participation/participation.module';
import { ArtemisExerciseHintManagementModule } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-management.module';
import { ArtemisModelingExerciseManagementModule } from 'app/exercises/modeling/manage/modeling-exercise-management.module';
import { ArtemisCourseScoresModule } from 'app/course/course-scores/course-scores.module';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { ArtemisComplaintsForTutorModule } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.module';
import { ArtemisFileUploadAssessmentModule } from 'app/exercises/file-upload/assess/file-upload-assessment.module';
import { ArtemisModelingAssessmentEditorModule } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.module';
import { CourseGroupComponent } from 'app/course/manage/course-group.component';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ArtemisModelingExerciseModule } from 'app/exercises/modeling/manage/modeling-exercise.module';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';
import { ArtemisListOfComplaintsModule } from 'app/complaints/list-of-complaints/list-of-complaints.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisCourseQuestionsModule } from 'app/course/course-questions/course-questions.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(courseManagementState),
        FormDateTimePickerModule,
        ReactiveFormsModule,
        ImageCropperModule,
        OrionModule,
        MomentModule,
        ArtemisExerciseModule,
        ArtemisLectureModule,
        ArtemisCourseScoresModule,
        ArtemisLearningGoalsModule,
        ArtemisExerciseScoresModule,
        ArtemisProgrammingExerciseManagementModule,
        ArtemisFileUploadExerciseManagementModule,
        ArtemisQuizManagementModule,
        ArtemisTextExerciseManagementModule,
        ArtemisModelingExerciseManagementModule,
        ArtemisProgrammingExerciseModule,
        ArtemisTextExerciseModule,
        ArtemisModelingExerciseModule,
        ArtemisColorSelectorModule,
        ArtemisDashboardsModule,
        ArtemisExerciseHintManagementModule,
        ArtemisParticipationModule,
        ArtemisComplaintsForTutorModule,
        ArtemisListOfComplaintsModule,
        ArtemisFileUploadAssessmentModule,
        ArtemisModelingAssessmentEditorModule,
        NgxDatatableModule,
        ArtemisDataTableModule,
        ArtemisAssessmentSharedModule,
        ArtemisCourseQuestionsModule,
        ArtemisSharedPipesModule,
    ],
    declarations: [CourseManagementComponent, CourseDetailComponent, CourseUpdateComponent, CourseExerciseCardComponent, CourseManagementExercisesComponent, CourseGroupComponent],
})
export class ArtemisCourseManagementModule {}
