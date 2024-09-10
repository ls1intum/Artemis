import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisFileUploadExerciseManagementModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.module';
import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { ArtemisColorSelectorModule } from 'app/shared/color-selector/color-selector.module';
import { MatChipsModule } from '@angular/material/chips';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseManagementExercisesComponent } from 'app/course/manage/course-management-exercises.component';
import { CourseDetailComponent } from 'app/course/manage/detail/course-detail.component';
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
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ArtemisModelingExerciseModule } from 'app/exercises/modeling/manage/modeling-exercise.module';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';
import { ArtemisListOfComplaintsModule } from 'app/complaints/list-of-complaints/list-of-complaints.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CourseManagementCardComponent } from 'app/course/manage/overview/course-management-card.component';
import { CourseManagementExerciseRowComponent } from './overview/course-management-exercise-row.component';
import { CourseManagementOverviewStatisticsComponent } from './overview/course-management-overview-statistics.component';
import { ArtemisTutorParticipationGraphModule } from 'app/shared/dashboards/tutor-participation-graph/tutor-participation-graph.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseManagementStatisticsComponent } from 'app/course/manage/course-management-statistics.component';
import { CourseDetailDoughnutChartComponent } from 'app/course/manage/detail/course-detail-doughnut-chart.component';
import { OrionCourseManagementExercisesComponent } from 'app/orion/management/orion-course-management-exercises.component';
import { CourseDetailLineChartComponent } from 'app/course/manage/detail/course-detail-line-chart.component';
import { UserImportModule } from 'app/shared/user-import/user-import.module';
import { CourseManagementExercisesSearchComponent } from 'app/course/manage/course-management-exercises-search.component';
import { LineChartModule, PieChartModule } from '@swimlane/ngx-charts';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { ArtemisChartsModule } from 'app/shared/chart/artemis-charts.module';
import { ImageCropperModule } from 'app/shared/image-cropper/image-cropper.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { ArtemisCourseGroupModule } from 'app/shared/course-group/course-group.module';
import { CourseGroupMembershipComponent } from './course-group-membership/course-group-membership.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { CourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/course-lti-configuration.component';
import { EditCourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/edit-course-lti-configuration.component';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseCategoriesModule } from 'app/shared/exercise-categories/exercise-categories.module';
import { CourseManagementTabBarComponent } from 'app/course/manage/course-management-tab-bar/course-management-tab-bar.component';
import { ArtemisExerciseCreateButtonsModule } from 'app/exercises/shared/manage/exercise-create-buttons.module';
import { ArtemisLearningPathManagementModule } from 'app/course/learning-paths/learning-path-management/learning-path-management.module';
import { IrisModule } from 'app/iris/iris.module';
import { DetailModule } from 'app/detail-overview-list/detail.module';
import { BuildQueueComponent } from 'app/localci/build-queue/build-queue.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal.component';
import { HeaderCourseComponent } from 'app/overview/header-course.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisFAQModule } from 'app/faq/faq.module';

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
        ArtemisCompetenciesModule,
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
        ArtemisCourseGroupModule,
        FeatureToggleModule,
        ExerciseCategoriesModule,
        NgbNavModule,
        ArtemisExerciseCreateButtonsModule,
        ArtemisLearningPathManagementModule,
        IrisModule,
        DetailModule,
        SubmissionResultStatusModule,
        ArtemisMarkdownEditorModule,
        ArtemisFAQModule,
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
        CourseManagementTabBarComponent,
        BuildQueueComponent,
        ImageCropperModalComponent,
        HeaderCourseComponent,
    ],
    exports: [HeaderCourseComponent],
})
export class ArtemisCourseManagementModule {}
