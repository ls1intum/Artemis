import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ModelingStatisticsComponent } from './modeling-statistics.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { JhiMainComponent } from 'app/shared/layouts/main/main.component';
import { HomeComponent } from 'app/home/home.component';

const ENTITY_STATES = [
    {
        path: 'course/:courseId/exercise/:exerciseId/statistics',
        component: ModelingStatisticsComponent,
        data: {
            authorities: ['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'],
            pageTitle: 'assessmentDashboard.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), NgbModule],
    declarations: [ModelingStatisticsComponent],
    entryComponents: [HomeComponent, ModelingStatisticsComponent, JhiMainComponent],
})
export class ArtemisModelingStatisticsModule {}
