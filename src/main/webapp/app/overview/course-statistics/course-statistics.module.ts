import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { RouterModule, Routes } from '@angular/router';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisExerciseScoresChartModule } from 'app/overview/visualizations/exercise-scores-chart.module';
import { NgxChartsModule } from '@swimlane/ngx-charts';
import { GradingKeyOverviewComponent } from 'app/grading-system/grading-key-overview/grading-key-overview.component';

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
    imports: [ArtemisExerciseScoresChartModule, ArtemisSharedModule, FontAwesomeModule, RouterModule.forChild(routes), NgxChartsModule],
    declarations: [CourseStatisticsComponent],
})
export class CourseStatisticsModule {}
