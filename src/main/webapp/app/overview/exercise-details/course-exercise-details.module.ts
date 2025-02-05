import { OrionCourseExerciseDetailsComponent } from 'app/orion/participation/orion-course-exercise-details.component';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { RouterModule, Routes } from '@angular/router';
import { NgModule } from '@angular/core';

import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisModelingEditorModule } from 'app/exercises/modeling/shared/modeling-editor.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';
import { LtiInitializerComponent } from 'app/overview/exercise-details/lti-initializer.component';
import { LtiInitializerModalComponent } from 'app/overview/exercise-details/lti-initializer-modal.component';
import { ArtemisProgrammingExerciseManagementModule } from 'app/exercises/programming/manage/programming-exercise-management.module';
import { ProblemStatementComponent } from 'app/overview/exercise-details/problem-statement/problem-statement.component';
import { ArtemisFeedbackModule } from 'app/exercises/shared/feedback/feedback.module';
import { ArtemisExerciseInfoModule } from 'app/exercises/shared/exercise-info/exercise-info.module';
import { IrisModule } from 'app/iris/iris.module';
import { DiscussionSectionComponent } from 'app/overview/discussion-section/discussion-section.component';
import { ExerciseHeadersInformationComponent } from 'app/exercises/shared/exercise-headers/exercise-headers-information/exercise-headers-information.component';

const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/orion/participation/orion-course-exercise-details.component').then((m) => m.OrionCourseExerciseDetailsComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.exercise',
        },
        pathMatch: 'full',
        canActivate: [UserRouteAccessService],
    },
];

const standaloneComponents = [ExerciseHeadersInformationComponent];

@NgModule({
    imports: [
        ArtemisExerciseButtonsModule,
        ArtemisCourseExerciseRowModule,

        ArtemisSharedComponentModule,
        ArtemisResultModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        ArtemisComplaintsModule,
        FontAwesomeModule,
        RouterModule.forChild(routes),
        ArtemisModelingEditorModule,
        SubmissionResultStatusModule,
        ArtemisProgrammingExerciseManagementModule,
        ArtemisFeedbackModule,
        ArtemisExerciseInfoModule,
        IrisModule,
        DiscussionSectionComponent,
        [...standaloneComponents],
        CourseExerciseDetailsComponent,
        OrionCourseExerciseDetailsComponent,
        LtiInitializerComponent,
        LtiInitializerModalComponent,
        ProblemStatementComponent,
    ],
    exports: [CourseExerciseDetailsComponent, OrionCourseExerciseDetailsComponent, ProblemStatementComponent],
})
export class CourseExerciseDetailsModule {}
