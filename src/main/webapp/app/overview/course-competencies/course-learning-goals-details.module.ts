import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisLectureUnitsModule } from 'app/overview/course-lectures/lecture-units.module';
import { NgModule } from '@angular/core';
import { ArtemisLearningGoalsModule } from 'app/course/competencies/learning-goal.module';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { Authority } from 'app/shared/constants/authority.constants';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { CourseLearningGoalsDetailsComponent } from 'app/overview/course-competencies/course-learning-goals-details.component';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { FireworksModule } from 'app/shared/fireworks/fireworks.module';

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
