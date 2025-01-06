import { NgModule } from '@angular/core';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { RouterModule, Routes } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisExerciseScoresChartModule } from 'app/overview/visualizations/exercise-scores-chart.module';
import { BarChartModule, PieChartModule } from '@swimlane/ngx-charts';

import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

const routes: Routes = [
    {
        path: '',
        loadComponent: () => import('app/overview/course-statistics/course-statistics.component').then((m) => m.CourseStatisticsComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.statistics',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'grading-key',
        loadComponent: () => import('app/grading-system/grading-key-overview/grading-key-overview.component').then((m) => m.GradingKeyOverviewComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.gradingSystem.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [
        ArtemisExerciseScoresChartModule,
        ArtemisSharedModule,
        FontAwesomeModule,
        RouterModule.forChild(routes),
        BarChartModule,
        PieChartModule,
        ArtemisSharedComponentModule,
        CourseStatisticsComponent,
    ],
})
export class CourseStatisticsModule {}
