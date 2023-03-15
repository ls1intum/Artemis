import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatChipsModule } from '@angular/material/chips';
import { RouterModule } from '@angular/router';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { LineChartModule, PieChartModule } from '@swimlane/ngx-charts';

import { CourseGroupMembershipComponent } from './course-group-membership/course-group-membership.component';
import { CourseManagementExerciseRowComponent } from './overview/course-management-exercise-row.component';
import { CourseManagementOverviewStatisticsComponent } from './overview/course-management-overview-statistics.component';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisComplaintsForTutorModule } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.module';
import { ArtemisListOfComplaintsModule } from 'app/complaints/list-of-complaints/list-of-complaints.module';
import { ArtemisCourseScoresModule } from 'app/course/course-scores/course-scores.module';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { CourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/course-lti-configuration.component';
import { EditCourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/edit-course-lti-configuration.component';
import { CourseManagementExercisesSearchComponent } from 'app/course/manage/course-management-exercises-search.component';
import { CourseManagementExercisesComponent } from 'app/course/manage/course-management-exercises.component';
import { CourseManagementStatisticsComponent } from 'app/course/manage/course-management-statistics.component';
import { CourseManagementComponent } from 'app/course/manage/course-management.component';
import { courseManagementState } from 'app/course/manage/course-management.route';
import { CourseUpdateComponent } from 'app/course/manage/course-update.component';
import { CourseDetailDoughnutChartComponent } from 'app/course/manage/detail/course-detail-doughnut-chart.component';
import { CourseDetailLineChartComponent } from 'app/course/manage/detail/course-detail-line-chart.component';
import { CourseDetailComponent } from 'app/course/manage/detail/course-detail.component';
import { CourseManagementCardComponent } from 'app/course/manage/overview/course-management-card.component';
import { ArtemisFileUploadAssessmentModule } from 'app/exercises/file-upload/assess/file-upload-assessment.module';
import { ArtemisFileUploadExerciseManagementModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.module';
import { ArtemisModelingAssessmentEditorModule } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.module';
import { ArtemisModelingExerciseManagementModule } from 'app/exercises/modeling/manage/modeling-exercise-management.module';
import { ArtemisModelingExerciseModule } from 'app/exercises/modeling/manage/modeling-exercise.module';
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';
import { ArtemisQuizManagementModule } from 'app/exercises/quiz/manage/quiz-management.module';
import { ArtemisExerciseModule } from 'app/exercises/shared/exercise/exercise.module';
import { ArtemisExerciseHintManagementModule } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-management.module';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { ArtemisParticipationModule } from 'app/exercises/shared/participation/participation.module';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisTextExerciseManagementModule } from 'app/exercises/text/manage/text-exercise-management.module';
import { ArtemisLectureModule } from 'app/lecture/lecture.module';
import { OrionCourseManagementExercisesComponent } from 'app/orion/management/orion-course-management-exercises.component';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { ArtemisChartsModule } from 'app/shared/chart/artemis-charts.module';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCourseGroupModule } from 'app/shared/course-group/course-group.module';
import { ArtemisDashboardsModule } from 'app/shared/dashboards/dashboards.module';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { ImageCropperModule } from 'app/shared/image-cropper/image-cropper.module';
import { UserImportModule } from 'app/shared/import/user-import.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        RouterModule.forChild(courseManagementState),
        FormDateTimePickerModule,
        ReactiveFormsModule,
        ImageCropperModule,
        OrionModule,
        MatChipsModule,
        ArtemisExerciseModule,
        ArtemisLectureModule,
        ArtemisFullscreenModule,
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
        ArtemisSharedPipesModule,
        ArtemisTutorParticipationGraphModule,
        ArtemisMarkdownModule,
        ArtemisSharedComponentModule,
        UserImportModule,
        LineChartModule,
        PieChartModule,
        ArtemisPlagiarismModule,
        ArtemisChartsModule,
        ArtemisCoursesModule,
        ArtemisCourseGroupModule,
        FeatureToggleModule,
    ],
    declarations: [
        CourseManagementComponent,
        CourseDetailComponent,
        CourseUpdateComponent,
        CourseExerciseCardComponent,
        CourseManagementExercisesComponent,
        OrionCourseManagementExercisesComponent,
        CourseManagementStatisticsComponent,
        CourseManagementCardComponent,
        CourseManagementExerciseRowComponent,
        CourseManagementOverviewStatisticsComponent,
        CourseDetailDoughnutChartComponent,
        CourseDetailLineChartComponent,
        CourseManagementExercisesSearchComponent,
        CourseGroupMembershipComponent,
        CourseLtiConfigurationComponent,
        EditCourseLtiConfigurationComponent,
    ],
})
export class ArtemisCourseManagementModule {}
