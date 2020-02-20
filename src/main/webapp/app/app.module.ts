import './vendor.ts';

import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ArtemisTutorCourseDashboardModule } from 'app/course/tutor-course-dashboard/tutor-course-dashboard.module';
import { ArtemisInstructorExerciseStatsDashboardModule } from 'app/exercises/shared/instructor-exercise-dashboard/instructor-exercise-dashboard.module';
import { ArtemisSystemNotificationModule } from 'app/core/system-notification/system-notification.module';
import { ArtemisExampleModelingSolutionModule } from 'app/exercises/modeling/manage/example-modeling-solution/example-modeling-solution.module';
import { ArtemisCourseScoresModule } from 'app/course/course-scores.module';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { ArtemisFileUploadSubmissionModule } from 'app/exercises/file-upload/participate/file-upload-submission/file-upload-submission.module';
import { ArtemisNotificationModule } from 'app/overview/notification/notification.module';
import { NotificationContainerComponent } from 'app/shared/layouts/notification-container/notification-container.component';
import { PageRibbonComponent } from 'app/shared/layouts/profiles/page-ribbon.component';
import { ArtemisComplaintsForTutorModule } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { SystemNotificationComponent } from 'app/shared/layouts/system-notification/system-notification.component';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { ArtemisFileUploadAssessmentModule } from 'app/exercises/file-upload/assess/file-upload-assessment.module';
import { ArtemisModelingStatisticsModule } from 'app/exercises/modeling/manage/modeling-statistics/modeling-statistics.module';
import { ArtemisExampleTextSubmissionModule } from 'app/exercises/text/manage/example-text-submission/example-text-submission.module';
import { ArtemisTextModule } from 'app/exercises/text/participate/text-editor/text-editor.module';
import { JhiMainComponent } from 'app/shared/layouts/main/main.component';
import { ArtemisExampleModelingSubmissionModule } from 'app/exercises/modeling/manage/example-modeling-submission/example-modeling-submission.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisOverviewModule } from 'app/overview/overview.module';
import { ArtemisConnectionNotificationModule } from 'app/shared/layouts/connection-notification/connection-notification.module';
import { ArtemisTextAssessmentModule } from 'app/exercises/text/assess/text-assessment/text-assessment.module';
import { ArtemisListOfComplaintsModule } from 'app/complaints/list-of-complaints/list-of-complaints.module';
import { ArtemisProgrammingSubmissionModule } from 'app/exercises/programming/participate/programming-submission/programming-submission.module';
import { FooterComponent } from 'app/shared/layouts/footer/footer.component';
import { ArtemisEntityModule } from 'app/entities/entity.module';
import { ArtemisParticipationModule } from 'app/exercises/shared/participation/participation.module';
import { ArtemisLegalModule } from 'app/core/legal/legal.module';
import { ArtemisTutorExerciseDashboardModule } from 'app/exercises/shared/tutor-exercise-dashboard/tutor-exercise-dashboard.module';
import { ActiveMenuDirective } from 'app/shared/layouts/navbar/active-menu.directive';
import { ErrorComponent } from 'app/shared/layouts/error/error.component';
import { ArtemisApollonDiagramsModule } from 'app/exercises/quiz/manage/apollon-diagrams/apollon-diagram.module';
import { ArtemisCoreModule } from 'app/core/core.module';
import { ArtemisModelingSubmissionModule } from 'app/exercises/modeling/participate/modeling-submission/modeling-submission.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisAccountModule } from 'app/account/account.module';
import { ArtemisInstructorCourseStatsDashboardModule } from 'app/course/instructor-course-dashboard/instructor-course-dashboard.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisHomeModule } from 'app/home/home.module';
import { OrionOutdatedComponent } from 'app/shared/orion/outdated-plugin-warning/orion-outdated.component';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { ArtemisQuizModule } from 'app/exercises/quiz/participate/quiz.module';
import { ArtemisStatisticModule } from 'app/exercises/quiz/manage/statistics/quiz-statistic.module';
import { ArtemisModelingAssessmentEditorModule } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.module';
import { QuizExerciseExportComponent } from 'app/exercises/quiz/manage/quiz-exercise-export.component';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';

@NgModule({
    imports: [
        StructuredGradingCriterionModule,
        BrowserModule,
        BrowserAnimationsModule,
        ArtemisSharedModule,
        ArtemisCoreModule,
        ArtemisHomeModule,
        ArtemisEntityModule,
        ArtemisAppRoutingModule,
        ArtemisConnectionNotificationModule,
        GuidedTourModule,
        ArtemisLegalModule,
        ArtemisParticipationModule.forRoot(),
        ArtemisTeamModule.forRoot(),
        ArtemisProgrammingSubmissionModule.forRoot(),
        ArtemisOverviewModule,
        ArtemisAccountModule,
        ArtemisApollonDiagramsModule,
        ArtemisQuizModule,
        ArtemisCourseScoresModule,
        ArtemisExerciseScoresModule,
        ArtemisStatisticModule,
        ArtemisModelingSubmissionModule,
        ArtemisModelingStatisticsModule,
        ArtemisTextModule,
        ArtemisTextAssessmentModule,
        ArtemisFileUploadSubmissionModule,
        ArtemisFileUploadAssessmentModule,
        ArtemisInstructorCourseStatsDashboardModule,
        ArtemisInstructorExerciseStatsDashboardModule,
        ArtemisTutorCourseDashboardModule,
        ArtemisTutorExerciseDashboardModule,
        ArtemisComplaintsModule,
        ArtemisComplaintsForTutorModule,
        ArtemisNotificationModule,
        ArtemisSystemNotificationModule,
        ArtemisModelingAssessmentEditorModule,
        ArtemisModelingSubmissionModule,
        ArtemisExampleTextSubmissionModule,
        ArtemisExampleModelingSubmissionModule,
        ArtemisExampleModelingSolutionModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisListOfComplaintsModule,
    ],
    declarations: [
        JhiMainComponent,
        NavbarComponent,
        ErrorComponent,
        OrionOutdatedComponent,
        PageRibbonComponent,
        ActiveMenuDirective,
        FooterComponent,
        NotificationContainerComponent,
        SystemNotificationComponent,
        // TODO: this should be much lower in the hierarchy
        QuizExerciseExportComponent,
    ],
    bootstrap: [JhiMainComponent],
})
export class ArtemisAppModule {}
