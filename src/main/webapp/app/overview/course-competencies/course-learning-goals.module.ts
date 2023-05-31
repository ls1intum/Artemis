import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { CourseLearningGoalsComponent } from 'app/overview/course-competencies/course-learning-goals.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisCompetenciesModule } from 'app/course/competencies/competency.module';
import { FireworksModule } from 'app/shared/fireworks/fireworks.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.learningGoals',
        },
        component: CourseLearningGoalsComponent,
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisCompetenciesModule, FireworksModule],
    declarations: [CourseLearningGoalsComponent],
    exports: [CourseLearningGoalsComponent],
})
export class CourseLearningGoalsModule {}
