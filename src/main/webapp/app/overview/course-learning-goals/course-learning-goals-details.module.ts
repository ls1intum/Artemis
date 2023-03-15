import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisLearningGoalsModule } from 'app/course/learning-goals/learning-goal.module';
import { CourseLearningGoalsDetailsComponent } from 'app/overview/course-learning-goals/course-learning-goals-details.component';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { Authority } from 'app/shared/constants/authority.constants';
import { FireworksModule } from 'app/shared/fireworks/fireworks.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';

const routes: Routes = [
    {
        path: '',
        component: CourseLearningGoalsDetailsComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.learningGoals',
        },
        canActivate: [UserRouteAccessService],
    },
];
@NgModule({
    imports: [
        RouterModule.forChild(routes),
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        ArtemisSharedPipesModule,
        ArtemisLectureUnitsModule,
        ArtemisLearningGoalsModule,
        ArtemisMarkdownModule,
        ArtemisSidePanelModule,
        FireworksModule,
    ],
    declarations: [CourseLearningGoalsDetailsComponent],
    exports: [CourseLearningGoalsDetailsComponent],
})
export class ArtemisCourseLearningGoalsDetailsModule {}
