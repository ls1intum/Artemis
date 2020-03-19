import './vendor.ts';

import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ArtemisSystemNotificationModule } from 'app/core/system-notification/system-notification.module';
import { ArtemisCourseScoresModule } from 'app/course/course-scores/course-scores.module';
import { NavbarComponent } from 'app/shared/layouts/navbar/navbar.component';
import { ArtemisFileUploadAssessmentModule } from 'app/exercises/file-upload/assess/file-upload-assessment.module';
import { ArtemisNotificationModule } from 'app/overview/notification/notification.module';
import { NotificationContainerComponent } from 'app/shared/layouts/notification-container/notification-container.component';
import { PageRibbonComponent } from 'app/shared/layouts/profiles/page-ribbon.component';
import { ArtemisComplaintsForTutorModule } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { SystemNotificationComponent } from 'app/shared/layouts/system-notification/system-notification.component';
import { ArtemisAppRoutingModule } from 'app/app-routing.module';
import { ArtemisExerciseScoresModule } from 'app/exercises/shared/exercise-scores/exercise-scores.module';
import { JhiMainComponent } from 'app/shared/layouts/main/main.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisCoursesModule } from 'app/overview/courses.module';
import { ArtemisConnectionNotificationModule } from 'app/shared/layouts/connection-notification/connection-notification.module';
import { ArtemisTextExerciseAssessmnetModule } from 'app/exercises/text/assess/text-assessment.module';
import { ArtemisListOfComplaintsModule } from 'app/complaints/list-of-complaints/list-of-complaints.module';
import { FooterComponent } from 'app/shared/layouts/footer/footer.component';
import { ArtemisLegalModule } from 'app/core/legal/legal.module';
import { ActiveMenuDirective } from 'app/shared/layouts/navbar/active-menu.directive';
import { ErrorComponent } from 'app/shared/layouts/error/error.component';
import { ArtemisCoreModule } from 'app/core/core.module';
import { GuidedTourModule } from 'app/guided-tour/guided-tour.module';
import { ArtemisAccountModule } from 'app/account/account.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisHomeModule } from 'app/home/home.module';
import { OrionOutdatedComponent } from 'app/shared/orion/outdated-plugin-warning/orion-outdated.component';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { ArtemisModelingAssessmentEditorModule } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.module';
import { ArtemisCourseManagementModule } from 'app/course/manage/course-management.module';
import { ArtemisExerciseParticipationModule } from 'app/exercises/exercise-participation.module';
import { StructuredGradingCriterionModule } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.module';

@NgModule({
    imports: [
        StructuredGradingCriterionModule,
        BrowserModule,
        BrowserAnimationsModule,
        ArtemisSharedModule,
        ArtemisCoreModule,
        ArtemisHomeModule,
        ArtemisCourseManagementModule,
        ArtemisAppRoutingModule,
        ArtemisConnectionNotificationModule,
        GuidedTourModule,
        ArtemisLegalModule,
        ArtemisTeamModule,
        ArtemisCoursesModule,
        ArtemisAccountModule,
        ArtemisExerciseParticipationModule,
        ArtemisCourseScoresModule,
        ArtemisExerciseScoresModule,
        ArtemisTextExerciseAssessmnetModule,
        ArtemisFileUploadAssessmentModule,
        ArtemisModelingAssessmentEditorModule,
        ArtemisSystemNotificationModule,
        ArtemisComplaintsModule,
        ArtemisComplaintsForTutorModule,
        ArtemisNotificationModule,
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
    ],
    bootstrap: [JhiMainComponent],
})
export class ArtemisAppModule {}
