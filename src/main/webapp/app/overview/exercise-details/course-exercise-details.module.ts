import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { RouterModule, Routes } from '@angular/router';
import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { OrionCourseExerciseDetailsComponent } from 'app/orion/participation/orion-course-exercise-details.component';
import { isOrion } from 'app/shared/orion/orion';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { LtiInitializerComponent } from 'app/overview/exercise-details/lti-initializer.component';
import { LtiInitializerModalComponent } from 'app/overview/exercise-details/lti-initializer-modal.component';
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import { ArtemisExerciseHintParticipationModule } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-participation.module';
import { ProblemStatementComponent } from 'app/overview/exercise-details/problem-statement/problem-statement.component';
import { ArtemisFeedbackModule } from 'app/exercises/shared/feedback/feedback.module';
import { ArtemisExerciseInfoModule } from 'app/exercises/shared/exercise-info/exercise-info.module';
import { IrisModule } from 'app/iris/iris.module';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/exercise-chatbot/exercise-chatbot-button.component';

const routes: Routes = [
    {
        path: '',
        component: !isOrion ? CourseExerciseDetailsComponent : OrionCourseExerciseDetailsComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.exercise',
        },
        pathMatch: 'full',
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [
        ArtemisExerciseButtonsModule,
        ArtemisCourseExerciseRowModule,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisSharedPipesModule,
        ArtemisResultModule,
        ArtemisSidePanelModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        OrionModule,
        ArtemisComplaintsModule,
        FeatureToggleModule,
        FontAwesomeModule,
        RatingModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        RouterModule.forChild(routes),
        ArtemisModelingEditorModule,
        ArtemisMarkdownModule,
        SubmissionResultStatusModule,
        ArtemisProgrammingExerciseManagementModule,
        ArtemisExerciseHintParticipationModule,
        ArtemisFeedbackModule,
        ArtemisExerciseInfoModule,
        IrisModule,
        DiscussionSectionComponent,
        IrisExerciseChatbotButtonComponent,
    ],
    declarations: [CourseExerciseDetailsComponent, OrionCourseExerciseDetailsComponent, LtiInitializerComponent, LtiInitializerModalComponent, ProblemStatementComponent],
    exports: [CourseExerciseDetailsComponent, OrionCourseExerciseDetailsComponent, ProblemStatementComponent],
})
export class CourseExerciseDetailsModule {}
