import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ArtemisFileUploadExerciseManagementModule } from 'app/exercises/file-upload/manage/file-upload-exercise-management.module';
import { CourseExerciseCardComponent } from 'app/course/manage/course-exercise-card.component';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { MatChipsModule } from '@angular/material/chips';

import { CourseManagementExercisesComponent } from 'app/course/manage/course-management-exercises.component';
import { CourseDetailComponent } from 'app/course/manage/detail/course-detail.component';
import { CourseUpdateComponent } from 'app/course/manage/course-update.component';
import { courseManagementState } from 'app/course/manage/course-management.route';
import { CourseManagementComponent } from 'app/course/manage/course-management.component';
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import { ArtemisQuizManagementModule } from 'app/exercises/quiz/manage/quiz-management.module';
import { ArtemisExerciseModule } from 'app/exercises/shared/exercise/exercise.module';
import { ArtemisLectureModule } from 'app/lecture/lecture.module';
import { ArtemisTextExerciseManagementModule } from 'app/exercises/text/manage/text-exercise-management.module';
import { ArtemisParticipationModule } from 'app/exercises/shared/participation/participation.module';
import { ArtemisModelingExerciseManagementModule } from 'app/exercises/modeling/manage/modeling-exercise-management.module';
import { ArtemisCourseScoresModule } from 'app/course/course-scores/course-scores.module';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { ArtemisFileUploadAssessmentModule } from 'app/exercises/file-upload/assess/file-upload-assessment.module';
import { ArtemisModelingAssessmentEditorModule } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.module';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ArtemisModelingExerciseModule } from 'app/exercises/modeling/manage/modeling-exercise.module';
import { ArtemisTextExerciseModule } from 'app/exercises/text/manage/text-exercise/text-exercise.module';
import { ArtemisProgrammingExerciseModule } from 'app/exercises/programming/shared/programming-exercise.module';
import { ArtemisListOfComplaintsModule } from 'app/complaints/list-of-complaints/list-of-complaints.module';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { CourseManagementCardComponent } from 'app/course/manage/overview/course-management-card.component';
import { CourseManagementExerciseRowComponent } from './overview/course-management-exercise-row.component';
import { CourseManagementOverviewStatisticsComponent } from './overview/course-management-overview-statistics.component';

import { CourseManagementStatisticsComponent } from 'app/course/manage/course-management-statistics.component';
import { CourseDetailDoughnutChartComponent } from 'app/course/manage/detail/course-detail-doughnut-chart.component';
import { OrionCourseManagementExercisesComponent } from 'app/orion/management/orion-course-management-exercises.component';
import { CourseDetailLineChartComponent } from 'app/course/manage/detail/course-detail-line-chart.component';
import { CourseManagementExercisesSearchComponent } from 'app/course/manage/course-management-exercises-search.component';
import { LineChartModule, PieChartModule } from '@swimlane/ngx-charts';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';

import { CourseGroupMembershipComponent } from './course-group-membership/course-group-membership.component';

import { CourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/course-lti-configuration.component';
import { EditCourseLtiConfigurationComponent } from 'app/course/manage/course-lti-configuration/edit-course-lti-configuration.component';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { CourseManagementTabBarComponent } from 'app/course/manage/course-management-tab-bar/course-management-tab-bar.component';
import { ArtemisExerciseCreateButtonsModule } from 'app/exercises/shared/manage/exercise-create-buttons.module';
import { IrisModule } from 'app/iris/iris.module';
import { DetailModule } from 'app/detail-overview-list/detail.module';
import { BuildQueueComponent } from 'app/localci/build-queue/build-queue.component';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal.component';
import { HeaderCourseComponent } from 'app/overview/header-course.component';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';

@NgModule({
    imports: [
        RouterModule.forChild(courseManagementState),
        FormDateTimePickerModule,
        ReactiveFormsModule,
        MatChipsModule,
        ArtemisExerciseModule,
        ArtemisLectureModule,
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
        ArtemisParticipationModule,
        ComplaintsForTutorComponent,
        ArtemisListOfComplaintsModule,
        ArtemisFileUploadAssessmentModule,
        ArtemisModelingAssessmentEditorModule,
        NgxDatatableModule,
        ArtemisAssessmentSharedModule,

        LineChartModule,
        PieChartModule,
        ArtemisPlagiarismModule,
        NgbNavModule,
        ArtemisExerciseCreateButtonsModule,
        IrisModule,
        DetailModule,
        SubmissionResultStatusModule,
        CourseLtiConfigurationComponent,
        EditCourseLtiConfigurationComponent,
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
        CourseManagementTabBarComponent,
        BuildQueueComponent,
        ImageCropperModalComponent,
        HeaderCourseComponent,
    ],
    exports: [HeaderCourseComponent],
})
export class ArtemisCourseManagementModule {}
