import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { BarChartModule, PieChartModule } from '@swimlane/ngx-charts';

import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { ArtemisExerciseScoresChartModule } from 'app/overview/visualizations/exercise-scores-chart.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { Authority } from 'app/shared/constants/authority.constants';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const routes: Routes = [
    {
        path: '',
        component: CourseStatisticsComponent,
        data: {
            authorities: [Authority.USER],
            pageTitle: 'overview.statistics',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'grading-key',
        component: GradingKeyOverviewComponent,
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
    ],
    declarations: [CourseStatisticsComponent],
})
export class CourseStatisticsModule {}
