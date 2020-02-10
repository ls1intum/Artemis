import './vendor.ts';

import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { ArtemisSharedModule } from './shared';
import { ArtemisCoreModule } from 'app/core';
import { ArtemisAppRoutingModule } from './app-routing.module';
import { ArtemisHomeModule } from './home';
import { ArtemisEntityModule } from './entities/entity.module';
import {
    ActiveMenuDirective,
    ErrorComponent,
    FooterComponent,
    JhiMainComponent,
    NavbarComponent,
    NotificationContainerComponent,
    PageRibbonComponent,
    SystemNotificationComponent,
} from './layouts';
import { ArtemisConnectionNotificationModule } from 'app/layouts/connection-notification/connection-notification.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisLegalModule } from 'app/legal';
import { ArtemisParticipationModule } from 'app/entities/participation/participation.module';
import { ArtemisProgrammingSubmissionModule } from 'app/programming-submission';
import { ArtemisOverviewModule } from 'app/overview';
import { ArtemisAccountModule } from 'app/account/account.module';
import { ArtemisApollonDiagramsModule } from 'app/apollon-diagrams';
import { ArtemisQuizModule } from 'app/quiz/participate';
import { ArtemisCourseScoresModule, ArtemisExerciseScoresModule } from 'app/scores';
import { ArtemisStatisticModule } from 'app/quiz/statistics/quiz-statistic.module';
import { ArtemisModelingSubmissionModule } from 'app/modeling-submission';
import { ArtemisModelingStatisticsModule } from 'app/modeling-statistics';
import { ArtemisTextModule } from 'app/text-editor';
import { ArtemisTextAssessmentModule } from 'app/text-assessment';
import { ArtemisFileUploadSubmissionModule } from 'app/file-upload-submission/file-upload-submission.module';
import { ArtemisFileUploadAssessmentModule } from 'app/file-upload-assessment/file-upload-assessment.module';
import { ArtemisInstructorCourseStatsDashboardModule } from 'app/instructor-course-dashboard';
import { ArtemisInstructorExerciseStatsDashboardModule } from 'app/instructor-exercise-dashboard';
import { ArtemisTutorCourseDashboardModule } from 'app/tutor-course-dashboard';
import { ArtemisTutorExerciseDashboardModule } from 'app/tutor-exercise-dashboard';
import { ArtemisComplaintsModule } from 'app/complaints';
import { ArtemisComplaintsForTutorModule } from 'app/complaints-for-tutor';
import { ArtemisNotificationModule } from 'app/entities/notification/notification.module';
import { ArtemisSystemNotificationModule } from 'app/entities/system-notification/system-notification.module';
import { ArtemisModelingAssessmentEditorModule } from 'app/modeling-assessment-editor/modeling-assessment-editor.module';
import { ArtemisExampleTextSubmissionModule } from 'app/example-text-submission';
import { ArtemisExampleModelingSubmissionModule } from 'app/example-modeling-submission';
import { ArtemisExampleModelingSolutionModule } from 'app/example-modeling-solution';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercise-headers';
import { ArtemisListOfComplaintsModule } from 'app/list-of-complaints';
import { QuizExerciseExportComponent } from 'app/entities/quiz-exercise/quiz-exercise-export.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { StructuredGradingInstructionModule } from 'app/structured-grading-instruction/structured-grading-instruction.module';

@NgModule({
    imports: [
        StructuredGradingInstructionModule,
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
