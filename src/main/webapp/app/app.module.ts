import './vendor.ts';

import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ArtemisTutorCourseDashboardModule } from 'app/tutor-course-dashboard/tutor-course-dashboard.module';
import { ArtemisInstructorExerciseStatsDashboardModule } from 'app/instructor-exercise-dashboard/instructor-exercise-dashboard.module';
import { ArtemisSystemNotificationModule } from 'app/entities/system-notification/system-notification.module';
import { ArtemisExampleModelingSolutionModule } from 'app/example-modeling-solution/example-modeling-solution.module';
import { ArtemisCourseScoresModule } from 'app/scores/course-scores.module';
import { NavbarComponent } from 'app/layouts/navbar/navbar.component';
import { ArtemisModelingAssessmentEditorModule } from 'app/modeling-assessment-editor/modeling-assessment-editor.module';
import { ArtemisFileUploadSubmissionModule } from 'app/file-upload-submission/file-upload-submission.module';
import { ArtemisNotificationModule } from 'app/entities/notification/notification.module';
import { NotificationContainerComponent } from 'app/layouts/notification-container/notification-container.component';
import { PageRibbonComponent } from 'app/layouts/profiles/page-ribbon.component';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor/complaints-for-tutor.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers/exercise-headers.module';
import { SystemNotificationComponent } from 'app/layouts/system-notification/system-notification.component';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { ArtemisExerciseScoresModule } from 'app/scores/exercise-scores.module';
import { ArtemisFileUploadAssessmentModule } from 'app/file-upload-assessment/file-upload-assessment.module';
import { ArtemisModelingStatisticsModule } from 'app/modeling-statistics/modeling-statistics.module';
import { QuizExerciseExportComponent } from 'app/entities/quiz-exercise/quiz-exercise-export.component';
import { ArtemisExampleTextSubmissionModule } from 'app/example-text-submission/example-text-submission.module';
import { ArtemisStatisticModule } from 'app/quiz/statistics/quiz-statistic.module';
import { ArtemisTextModule } from 'app/text-editor/text-editor.module';
import { JhiMainComponent } from 'app/layouts/main/main.component';
import { ArtemisExampleModelingSubmissionModule } from 'app/example-modeling-submission/example-modeling-submission.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisOverviewModule } from 'app/overview/overview.module';
import { ArtemisConnectionNotificationModule } from 'app/layouts/connection-notification/connection-notification.module';
import { ArtemisTextAssessmentModule } from 'app/text-assessment/text-assessment.module';
import { ArtemisListOfComplaintsModule } from 'app/list-of-complaints/list-of-complaints.module';
import { ArtemisProgrammingSubmissionModule } from 'app/programming-submission/programming-submission.module';
import { FooterComponent } from 'app/layouts/footer/footer.component';
import { ArtemisEntityModule } from 'app/entities/entity.module';
import { ArtemisParticipationModule } from 'app/entities/participation/participation.module';
import { ArtemisLegalModule } from 'app/legal/legal.module';
import { ArtemisTutorExerciseDashboardModule } from 'app/tutor-exercise-dashboard/tutor-exercise-dashboard.module';
import { ActiveMenuDirective } from 'app/layouts/navbar/active-menu.directive';
import { ErrorComponent } from 'app/layouts/error/error.component';
import { ArtemisApollonDiagramsModule } from 'app/apollon-diagrams/apollon-diagrams.module';
import { ArtemisCoreModule } from 'app/core/core.module';
import { ArtemisModelingSubmissionModule } from 'app/modeling-submission/modeling-submission.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisAccountModule } from 'app/account/account.module';
import { ArtemisQuizModule } from 'app/quiz/participate/quiz.module';
import { ArtemisInstructorCourseStatsDashboardModule } from 'app/instructor-course-dashboard/instructor-course-dashboard.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisHomeModule } from 'app/home/home.module';
import { StructuredGradingCriterionModule } from 'app/structured-grading-criterion/structured-grading-criterion.module';

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
