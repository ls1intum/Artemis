import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { ArtemisProgrammingExerciseInstructionsRenderModule } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instructions-render.module';
import { RouterModule, Routes } from '@angular/router';

import { NgModule } from '@angular/core';
import { ChartsModule } from 'ng2-charts';
import { ClipboardModule } from 'ngx-clipboard';
import { MomentModule } from 'ngx-moment';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { OrionModule } from 'app/shared/orion/orion.module';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ProgrammingExerciseUtilsModule } from 'app/exercises/programming/shared/utils/programming-exercise-utils.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisCoursesRoutingModule } from 'app/overview/courses-routing.module';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { ArtemisExerciseButtonsModule } from 'app/overview/exercise-details/exercise-buttons.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

const routes: Routes = [
    {
        path: '',
        component: CourseExerciseDetailsComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.exercise',
        },
        canActivate: [UserRouteAccessService],
        children: [
            {
                path: '',
                pathMatch: 'full',
                loadChildren: () => import('app/overview/student-questions/student-questions.module').then((m) => m.ArtemisStudentQuestionsModule),
            },
        ],
    },
];

@NgModule({
    imports: [
        ArtemisExerciseButtonsModule,
        ArtemisCourseExerciseRowModule,
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ChartsModule,
        ClipboardModule,
        MomentModule,
        ArtemisSharedPipesModule,
        ArtemisResultModule,
        ArtemisSidePanelModule,
        ArtemisCoursesRoutingModule,
        ArtemisHeaderExercisePageWithDetailsModule,
        OrionModule,
        ArtemisComplaintsModule,
        FeatureToggleModule,
        ProgrammingExerciseUtilsModule,
        ArtemisTeamModule,
        RatingModule,
        ArtemisLearningGoalsModule,
        ArtemisProgrammingExerciseInstructionsRenderModule,
        RouterModule.forChild(routes),
        ArtemisMarkdownModule,
    ],
    declarations: [CourseExerciseDetailsComponent],
    exports: [CourseExerciseDetailsComponent],
})
export class CourseExerciseDetailsModule {}
